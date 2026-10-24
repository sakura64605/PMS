"""Read-only verification of admin login, PMS job definitions and recent callbacks."""
import http.cookiejar, json, sys, urllib.parse, urllib.request
from pathlib import Path
home = Path('/opt/pms/xxl-job')
credentials = json.loads((home / 'credentials.json').read_text())
meta = json.loads((home / 'admin-meta.json').read_text())
client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def admin(path, fields):
    with client.open(urllib.request.Request('http://127.0.0.1:9090/xxl-job-admin' + path,
                                           urllib.parse.urlencode(fields).encode()), timeout=15) as response:
        return json.load(response)
assert admin('/login', {'userName':credentials['adminUser'],'password':credentials['adminPassword'],'ifRemember':'off'}).get('code') == 200
rows = admin('/jobinfo/pageList', {'start':0,'length':100,'jobGroup':meta['groupId'],'triggerStatus':-1,
                                  'jobDesc':'','executorHandler':'','author':''})['data']
expected = set(meta['jobIds'].values())
rows = [row for row in rows if row['id'] in expected]
assert len(rows) == 5
results = []
for row in rows:
    logs = admin('/joblog/pageList', {'start':0,'length':10,'jobGroup':meta['groupId'],'jobId':row['id'],
                                     'logStatus':-1,'filterTime':''}).get('data', [])
    good = [log for log in logs if log.get('handleCode') == 200]
    if '--live' in sys.argv:
        assert row['triggerStatus'] == 1
        assert good, 'No successful callback for ' + row['executorHandler']
    results.append({'handler':row['executorHandler'],'jobId':row['id'],'enabled':row['triggerStatus'],
                    'recentSuccessfulCallbacks':len(good),'latestHandleCode':logs[0]['handleCode'] if logs else None})
print(json.dumps({'adminLogin':True,'jobs':results}))
