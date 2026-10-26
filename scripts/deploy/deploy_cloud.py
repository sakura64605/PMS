"""Run on the PMS host: install a checked Cloud release and atomically switch Nginx.

Preserves live application/XXL credentials; rolls back routing, jar and unit changes
on failed readiness. Uploaded jars must contain no developer application.yaml.
"""
import argparse
import hashlib
import json
import os
import re
import secrets
import shutil
import socket
import subprocess
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path
import yaml
from promote_backend import assemble

SERVICES = ['pms-cloud-registry', 'pms-cloud-config', 'pms-cloud-search-1',
            'pms-cloud-search-2', 'pms-cloud-gateway']
UNITS = Path('/etc/systemd/system')
NGINX = Path('/etc/nginx/conf.d/pms.conf')
DROPIN = UNITS / 'pms.service.d/cloud.conf'
CLIENT = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def run(args):
    subprocess.run(args, check=True, capture_output=True, timeout=90)


def digest(path):
    result = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()


def atomic(path, data, mode=0o644):
    temporary = path.with_name(path.name + '.cloud-stage')
    temporary.write_bytes(data)
    temporary.chmod(mode)
    os.replace(temporary, path)


def sudo_write(path, data):
    temporary = release / ('install-' + path.name)
    temporary.write_bytes(data)
    run(['sudo', '-n', 'install', '-m', '644', str(temporary), str(path)])


def get(url):
    with CLIENT.open(url, timeout=8) as response:
        return json.load(response)


def wait(url, business=False, seconds=240):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        try:
            value = get(url)
            if (value.get('code') == 200) if business else (value.get('status') == 'UP'):
                return
        except Exception:
            pass
        time.sleep(2)
    raise RuntimeError('Readiness failed: ' + url)


def unit(name, jar, port, heap, extra=()):
    dependency = '' if name in ('registry', 'config') else '\nAfter=pms-cloud-registry.service pms-cloud-config.service\nWants=pms-cloud-registry.service pms-cloud-config.service'
    return f'''[Unit]
Description=PMS Cloud {name}
After=network-online.target{dependency}
Wants=network-online.target
[Service]
Type=simple
User=ubuntu
WorkingDirectory={release}
EnvironmentFile={release}/env/{name}.env
ExecStart=/usr/bin/java -Xms32m -Xmx{heap}m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -XX:MaxDirectMemorySize=48m -Duser.timezone=Asia/Shanghai -jar {release}/{jar} --server.port={port} --server.address=127.0.0.1 {' '.join(extra)}
Restart=on-failure
RestartSec=8
TimeoutStopSec=40
SuccessExitStatus=143
[Install]
WantedBy=multi-user.target
'''.encode()


def main():
    manifest = json.loads((release / 'manifest.json').read_text())
    current = Path('/opt/pms/pms.jar')
    assert digest(current) == args.expected_sha, 'Live jar changed; refusing promotion'
    for name, expected in manifest['artifacts'].items():
        assert Path(name).name == name and digest(release / name) == expected, 'Artifact mismatch'
    assert not DROPIN.exists(), 'Cloud drop-in already exists; use a subsequent-release procedure'
    for port in (8761, 8888, 8181, 8182, 8088):
        with socket.socket() as probe:
            probe.settimeout(.5)
            assert probe.connect_ex(('127.0.0.1', port)) != 0, 'Cloud port occupied: ' + str(port)
    for name in SERVICES:
        assert not (UNITS / (name + '.service')).exists(), 'Existing Cloud service must not be overwritten'
    assert NGINX.read_text().count('proxy_pass http://127.0.0.1:8080;') == 3
    with zipfile.ZipFile(current) as source:
        config = yaml.safe_load(source.read('BOOT-INF/classes/application.yaml'))
        prod = yaml.safe_load(source.read('BOOT-INF/classes/application-prod.yaml')) or {}
        config['jwt'].update(prod.get('jwt', {}))
    secret = str(config['jwt']['secret'])
    assert len(secret.encode()) >= 32 and '\n' not in secret and '\r' not in secret and not secret.startswith('${')
    assert re.fullmatch(r'[A-Za-z0-9+/=_\-:.]+', secret), 'JWT secret needs systemd environment escaping'
    token = secrets.token_hex(32)
    env = release / 'env'; env.mkdir(mode=0o700)
    common = 'PMS_CONFIG_URL=http://127.0.0.1:8888\nPMS_EUREKA_URL=http://127.0.0.1:8761/eureka/\n'
    for name in ['registry', 'config', 'search-1', 'search-2', 'gateway', 'core']:
        extra = ''
        if name == 'config': extra = 'PMS_CONFIG_LOCATION=file:' + str(release / 'config') + '/\n'
        if name.startswith('search-'): extra = 'PMS_SEARCH_SERVICE_TOKEN=' + token + '\n'
        if name == 'core': extra = 'PMS_SEARCH_TOKEN=' + token + '\n'
        if name == 'gateway': extra = 'PMS_GATEWAY_JWT_SECRET=' + secret + '\n'
        atomic(env / (name + '.env'), (common + extra).encode(), 0o600)
    # Explicitly override local demo leases in the server installation.
    cloud = ['--spring.profiles.active=cloud', '--eureka.instance.lease-renewal-interval-in-seconds=15',
             '--eureka.instance.lease-expiration-duration-in-seconds=45', '--eureka.client.registry-fetch-interval-seconds=5']
    definitions = {
        'registry': unit('registry', 'registry.jar', 8761, 128),
        'config': unit('config', 'config.jar', 8888, 96),
        'search-1': unit('search-1', 'search.jar', 8181, 128, cloud + ['--pms.instance-id=search-1']),
        'search-2': unit('search-2', 'search.jar', 8182, 128, cloud + ['--pms.instance-id=search-2']),
        'gateway': unit('gateway', 'gateway.jar', 8088, 160, cloud),
    }
    nginx_before = NGINX.read_bytes()
    shutil.copy2(current, release / 'backend-before.jar')
    (release / 'nginx-before.conf').write_bytes(nginx_before)
    previous_pointer = Path('/opt/pms/releases/current.json').read_bytes()
    (release / 'current-before.json').write_bytes(previous_pointer)
    assembled = release / 'backend-live-config.jar'
    assemble(current, release / 'core.jar', assembled)
    switched = False
    installed = []
    try:
        for name in definitions:
            full = 'pms-cloud-' + name
            sudo_write(UNITS / (full + '.service'), definitions[name]); installed.append(full)
        run(['sudo', '-n', 'systemctl', 'daemon-reload'])
        for name, port in [('registry', 8761), ('config', 8888), ('search-1', 8181), ('search-2', 8182)]:
            run(['sudo', '-n', 'systemctl', 'start', 'pms-cloud-' + name])
            wait(f'http://127.0.0.1:{port}/actuator/health')
            print(json.dumps({'ready': name}), flush=True)
        dropin = f'''[Unit]
Wants=pms-cloud-registry.service pms-cloud-config.service
After=pms-cloud-registry.service pms-cloud-config.service
[Service]
EnvironmentFile={env}/core.env
ExecStart=
ExecStart=/usr/bin/java -Xms128m -Xmx1024m -XX:ActiveProcessorCount=2 -Xlog:gc*:file=/opt/pms/logs/gc-%%t.log:time,uptime,level,tags:filecount=5,filesize=20m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/opt/pms/logs/ -Duser.timezone=Asia/Shanghai -jar /opt/pms/pms.jar --spring.profiles.active=prod,cloud --server.address=127.0.0.1 --pms.search.mode=cloud --pms.search.rebuild-on-startup=false --spring.redisson.config=file:/opt/pms/config/redisson.yml --eureka.instance.lease-renewal-interval-in-seconds=15 --eureka.instance.lease-expiration-duration-in-seconds=45 --eureka.client.registry-fetch-interval-seconds=5
'''.encode()
        assert digest(current) == args.expected_sha, 'Live jar changed during preparation'
        run(['sudo', '-n', 'systemctl', 'stop', 'pms'])
        switched = True
        stage = current.with_name('pms-cloud-stage.jar'); shutil.copy2(assembled, stage); os.replace(stage, current)
        sudo_write(DROPIN, dropin)
        run(['sudo', '-n', 'systemctl', 'daemon-reload'])
        run(['sudo', '-n', 'systemctl', 'start', 'pms'])
        wait('http://127.0.0.1:8080/pet-system/pet/list?pageSize=1', True)
        print(json.dumps({'ready': 'core'}), flush=True)
        run(['sudo', '-n', 'systemctl', 'start', 'pms-cloud-gateway'])
        wait('http://127.0.0.1:8088/actuator/health')
        wait('http://127.0.0.1:8088/pet-system/pet/list?pageSize=1', True, 90)
        wait('http://127.0.0.1:8088/pet-system/search/global?keyword=cat&pageSize=1', True, 90)
        candidate = nginx_before.decode().replace('proxy_pass http://127.0.0.1:8080;', 'proxy_pass http://127.0.0.1:8088;')
        frontend = release / 'frontend'
        if (frontend / 'index.html').is_file():
            assert candidate.count('root /var/www/html/pms;') == 1, 'Unexpected frontend root'
            # Keep hashed assets requested by clients that still hold the old index.
            previous_assets = Path('/var/www/html/pms/assets')
            if previous_assets.exists(): shutil.copytree(previous_assets, frontend / 'assets', dirs_exist_ok=True)
            candidate = candidate.replace('root /var/www/html/pms;', 'root ' + str(frontend) + ';')
        sudo_write(NGINX, candidate.encode())
        run(['sudo', '-n', 'nginx', '-t']); run(['sudo', '-n', 'systemctl', 'reload', 'nginx'])
        wait('http://127.0.0.1/pet-system/pet/list?pageSize=1', True, 45)
        wait('http://127.0.0.1/pet-system/search/global?keyword=cat&pageSize=1', True, 45)
        run(['sudo', '-n', 'systemctl', 'enable', *SERVICES])
        result = {'ok': True, 'release': str(release), 'commit': manifest['commit'],
                  'backendSha': digest(current), 'backup': str(release / 'backend-before.jar'),
                  'services': SERVICES, 'preservedLiveConfiguration': True}
        atomic(release / 'deployment.json', json.dumps(result, indent=2).encode())
        atomic(Path('/opt/pms/releases/current.json'), json.dumps(result, indent=2).encode())
        print(json.dumps(result), flush=True)
    except BaseException:
        rollback(switched, installed)
        raise


def rollback(switched=True, installed=SERVICES):
    # Restore routing first and then the exact previous backend/configuration.
    sudo_write(NGINX, (release / 'nginx-before.conf').read_bytes())
    run(['sudo', '-n', 'nginx', '-t']); run(['sudo', '-n', 'systemctl', 'reload', 'nginx'])
    if switched:
        run(['sudo', '-n', 'systemctl', 'stop', 'pms'])
        stage = Path('/opt/pms/pms-rollback-stage.jar')
        shutil.copy2(release / 'backend-before.jar', stage); os.replace(stage, '/opt/pms/pms.jar')
        run(['sudo', '-n', 'rm', '-f', str(DROPIN)])
    for name in reversed(installed):
        run(['sudo', '-n', 'systemctl', 'disable', '--now', name])
        run(['sudo', '-n', 'rm', '-f', str(UNITS / (name + '.service'))])
    run(['sudo', '-n', 'systemctl', 'daemon-reload'])
    run(['sudo', '-n', 'systemctl', 'start', 'pms'])
    wait('http://127.0.0.1:8080/pet-system/pet/list?pageSize=1', True)
    atomic(Path('/opt/pms/releases/current.json'), (release / 'current-before.json').read_bytes())
    print(json.dumps({'rolledBack': True, 'release': str(release)}), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--release', type=Path, required=True)
    parser.add_argument('--expected-sha')
    parser.add_argument('--rollback', action='store_true')
    args = parser.parse_args()
    release = args.release.resolve()
    assert release.parent == Path('/opt/pms/releases') and re.fullmatch(r'cloud-[a-z0-9-]+', release.name)
    if args.rollback: rollback()
    else: main()
