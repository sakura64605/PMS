"""Read-only HTTP checks and reversible search-worker fault injection for cloud_stack.py."""
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLIENT = urllib.request.build_opener(urllib.request.ProxyHandler({}))
BASE = 'http://127.0.0.1:8088'


def get(url):
    request = urllib.request.Request(url, headers={'Accept': 'application/json'})
    try:
        response = CLIENT.open(request, timeout=12)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read()
        return response.status, dict(response.headers), json.loads(raw) if raw else {}


def search():
    return get(BASE + '/pet-system/search/global?keyword=%E5%AE%A0&pageSize=5')


def healthy_search():
    status, headers, body = search()
    assert status == 200 and body.get('code') == 200, ('Search failed', status, body.get('code'))
    return headers, body['data']


def worker(action, name):
    subprocess.run([sys.executable, str(ROOT / 'scripts/local/cloud_stack.py'), action, name], check=True)


def count(port):
    status, _, body = get(f'http://127.0.0.1:{port}/actuator/metrics/pms.search.requests')
    assert status == 200
    return sum(row['value'] for row in body['measurements'] if row['statistic'] == 'COUNT')


def main():
    report = {}
    _, _, registry = get('http://127.0.0.1:8761/eureka/apps')
    applications = registry['applications']['application']
    search_app = next(a for a in applications if a['name'] == 'PMS-SEARCH-SERVICE')
    assert len(search_app['instance']) == 2
    report['registered_search_instances'] = 2
    _, _, config = get('http://127.0.0.1:8888/pms-core-service/cloud')
    assert any(s['source'].get('pms.search.mode') == 'cloud' for s in config['propertySources'])
    assert get(BASE + '/pet-system/user/info')[0] == 401
    assert get(BASE + '/internal/search/v1/query')[0] in (401, 403, 404)
    report['configuration_and_gateway_guard'] = True

    results = [healthy_search() for _ in range(20)]
    assert all(data == results[0][1] for _, data in results)
    report['distribution'] = {'search-1': count(8181), 'search-2': count(8182)}
    assert all(n > 0 for n in report['distribution'].values())
    trace = next((value for key, value in results[-1][0].items() if key.lower() == 'x-trace-id'), None)
    assert trace and any(trace in (ROOT / f'target/cloud-validation/search-{n}.out.log').read_text(encoding='utf-8') for n in (1, 2))
    report['cross_service_trace'] = True

    stopped = []
    try:
        worker('--stop-service', 'search-1'); stopped.append('search-1')
        for _ in range(8):
            healthy_search()
        report['single_worker_failure'] = '8/8 successful'
        worker('--stop-service', 'search-2'); stopped.append('search-2')
        failures = []
        for _ in range(8):
            start = time.monotonic()
            status, _, body = search()
            assert status != 200 or body.get('code') != 200
            failures.append(round(time.monotonic() - start, 3))
        status, _, body = get(BASE + '/pet-system/pet/list?pageSize=1')
        assert status == 200 and body.get('code') == 200
        _, _, metric = get('http://127.0.0.1:8180/actuator/metrics/resilience4j.circuitbreaker.state?tag=name:searchCandidates&tag=state:open')
        assert any(m['value'] == 1 for m in metric['measurements'])
        report['all_workers_down'] = {'isolated': True, 'breaker_open': True, 'durations_seconds': failures}
    finally:
        for name in stopped:
            worker('--restart', name)
        if stopped:
            deadline = time.monotonic() + 40
            while True:
                try:
                    healthy_search()
                    report['recovery'] = True
                    break
                except AssertionError:
                    if time.monotonic() > deadline:
                        raise
                    time.sleep(1)
    destination = ROOT / 'target/cloud-validation/verification.json'
    destination.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
