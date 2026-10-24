"""Deploy a tested backend, switch to XXL atomically, and roll back scheduling and jar on failure."""
import hashlib, http.cookiejar, json, os, re, shutil, subprocess, time, urllib.parse, urllib.request, zipfile
from pathlib import Path
from datetime import datetime
from zoneinfo import ZoneInfo

base = Path('/opt/pms')
home = base / 'xxl-job'
current = base / 'pms.jar'
incoming = base / 'pms-xxl-upload.jar'
credentials = json.loads((home / 'credentials.json').read_text())
admin_meta = json.loads((home / 'admin-meta.json').read_text())
stamp = datetime.now(ZoneInfo('Asia/Shanghai')).strftime('%Y%m%d-%H%M%S')

def run(args, **kwargs):
    return subprocess.run(args, check=True, text=True, capture_output=True, **kwargs)
def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024*1024), b''):
            digest.update(block)
    return digest.hexdigest()
assert sha(current) == os.environ['PMS_EXPECTED_SHA'], 'Live backend changed'
assert sha(incoming) == os.environ['PMS_UPLOAD_SHA'], 'Upload checksum mismatch'

cookies = http.cookiejar.CookieJar()
client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies))
def admin(path, values):
    request = urllib.request.Request('http://127.0.0.1:9090/xxl-job-admin' + path,
                                     urllib.parse.urlencode(values).encode())
    with client.open(request, timeout=15) as response:
        return json.load(response)
assert admin('/login', {'userName': credentials['adminUser'], 'password': credentials['adminPassword'],
                        'ifRemember': 'off'}).get('code') == 200, 'Admin login failed'
job_ids = admin_meta['jobIds']
def stop_jobs():
    for job in job_ids.values():
        try: admin('/jobinfo/stop', {'id': job})
        except Exception: pass
# Preflight must not accidentally switch another active deployment.
rows = admin('/jobinfo/pageList', {'start':0, 'length':100, 'jobGroup':admin_meta['groupId'],
                                  'triggerStatus':-1, 'jobDesc':'', 'executorHandler':'', 'author':''}).get('data', [])
assert all(not row['triggerStatus'] for row in rows if row['id'] in job_ids.values()), 'PMS jobs already enabled'

dropin = Path('/etc/systemd/system/pms.service.d/xxl-job.conf')
dropin_previous = dropin.read_bytes() if dropin.exists() else None
assert dropin_previous is None, 'Existing scheduling drop-in needs reconciliation'
backup = base / ('pms.jar.bak-xxl-' + stamp)
staged = base / ('pms-xxl-stage-' + stamp + '.jar')
pattern = re.compile(r'^BOOT-INF/classes/application(?:-[A-Za-z0-9_-]+)?\.(?:yaml|yml|properties)$')
with zipfile.ZipFile(current) as deployed:
    configs = {info.filename:deployed.read(info) for info in deployed.infolist() if pattern.match(info.filename)}
with zipfile.ZipFile(incoming) as source, zipfile.ZipFile(staged, 'w') as output:
    names = set()
    for info in source.infolist():
        names.add(info.filename)
        output.writestr(info, configs.get(info.filename, source.read(info)))
    for name, data in configs.items():
        if name not in names: output.writestr(name, data)
with zipfile.ZipFile(staged) as check:
    for name, data in configs.items(): assert check.read(name) == data
    assert any(name.startswith('BOOT-INF/lib/xxl-job-core-3.1.0') for name in check.namelist())
shutil.copy2(current, backup)
dropin_staged = home / 'pms-xxl-dropin.conf'
dropin_staged.write_text('[Unit]\nRequires=pms-xxl-network.service\nAfter=pms-xxl-network.service\n'
                         '[Service]\nEnvironmentFile=/opt/pms/xxl-job/executor.env\n')
# XXL's transport listens on all interfaces; permit only loopback access on this new port.
network_unit = Path('/etc/systemd/system/pms-xxl-network.service')
assert not network_unit.exists(), 'Network guard unit already exists'
network_staged = home / 'pms-xxl-network.service'
network_staged.write_text("""[Unit]
Description=PMS XXL executor loopback-only network guard
Before=pms.service
[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/bin/sh -c '/usr/sbin/iptables -C INPUT -p tcp --dport 9999 ! -s 127.0.0.1/32 -j REJECT 2>/dev/null || /usr/sbin/iptables -I INPUT 1 -p tcp --dport 9999 ! -s 127.0.0.1/32 -j REJECT'
ExecStart=/bin/sh -c '/usr/sbin/ip6tables -C INPUT -p tcp --dport 9999 ! -s ::1/128 -j REJECT 2>/dev/null || /usr/sbin/ip6tables -I INPUT 1 -p tcp --dport 9999 ! -s ::1/128 -j REJECT'
[Install]
WantedBy=multi-user.target
""")
def restart():
    run(['sudo','-n','systemctl','daemon-reload'])
    run(['sudo','-n','systemctl','restart','pms'], timeout=45)
def ready():
    for _ in range(90):
        try:
            with urllib.request.urlopen('http://127.0.0.1:8080/pet-system/pet/list?pageNum=1&pageSize=1',timeout=3) as response:
                if json.load(response).get('code') == 200: return True
        except Exception: pass
        time.sleep(2)
    return False
def beat():
    request = urllib.request.Request('http://127.0.0.1:9999/beat', b'{}',
                                    {'Content-Type':'application/json', 'XXL-JOB-ACCESS-TOKEN':credentials['accessToken']})
    with urllib.request.urlopen(request, timeout=3) as response: return json.load(response).get('code') == 200
def log_rows(job):
    return admin('/joblog/pageList', {'start':0,'length':10,'jobGroup':admin_meta['groupId'],
                                     'jobId':job,'logStatus':-1,'filterTime':''}).get('data', [])
results = {}
try:
    run(['sudo','-n','mkdir','-p',str(dropin.parent)])
    run(['sudo','-n','install','-m','644',str(network_staged),str(network_unit)])
    run(['sudo','-n','install','-m','644',str(dropin_staged),str(dropin)])
    shutil.copy2(staged, base / 'pms.xxl-next.jar')
    os.replace(base / 'pms.xxl-next.jar', current)
    restart()
    assert ready(), 'Backend readiness timed out'
    assert beat(), 'XXL executor is not responding'
    registered = False
    for _ in range(45):
        groups = admin('/jobgroup/pageList', {'start':0,'length':10,'appname':'pms-executor','title':''}).get('data', [])
        if any(group['id'] == admin_meta['groupId'] and '127.0.0.1:9999' in (group.get('addressList') or '') for group in groups):
            registered = True
            break
        time.sleep(2)
    assert registered, 'Executor not registered with admin'
    # Run each job once before enabling periodic triggers, including end-to-end result callbacks.
    for handler, job in job_ids.items():
        previous = {row['id'] for row in log_rows(job)}
        assert admin('/jobinfo/trigger', {'id':job,'executorParam':'','addressList':''}).get('code') == 200
        outcome = None
        for _ in range(60):
            records = [row for row in log_rows(job) if row['id'] not in previous]
            finished = [row for row in records if row.get('handleCode') in (200,500)]
            if finished:
                outcome = finished[0]
                break
            time.sleep(1)
        assert outcome and outcome.get('handleCode') == 200, 'Job smoke failed: ' + handler
        results[handler] = {'jobId':job, 'logId':outcome['id'], 'handleCode':outcome['handleCode']}
    for job in job_ids.values():
        assert admin('/jobinfo/start', {'id':job}).get('code') == 200, 'Job activation failed'
except Exception:
    stop_jobs()
    shutil.copy2(backup, base / 'pms.xxl-rollback.jar')
    os.replace(base / 'pms.xxl-rollback.jar', current)
    if dropin.exists(): run(['sudo','-n','rm','--',str(dropin)])
    restart()
    print(json.dumps({'ok':False,'rolledBack':True,'oldServiceReady':ready(),'backup':str(backup)}))
    raise
metadata = {'ok':True,'timestamp':stamp,'commit':os.environ['PMS_RELEASE_COMMIT'],
            'backendSha':sha(current),'buildSha':sha(incoming),'backup':str(backup),
            'adminUrl':admin_meta['adminUrl'],'mode':'xxl','jobSmoke':results,
            'preservedConfigs':{name:hashlib.sha256(data).hexdigest() for name,data in configs.items()}}
(home / 'deployment.json').write_text(json.dumps(metadata, indent=2))
admin_meta['pmsSchedulingSwitched'] = True
(home / 'admin-meta.json').write_text(json.dumps(admin_meta, indent=2))
print(json.dumps(metadata))
