"""Promote a checked backend jar while preserving live application configuration.
Run on the existing PMS host; does not install or switch the search microservice.
"""
import argparse,hashlib,json,os,re,shutil,subprocess,time,urllib.request,zipfile
from pathlib import Path
from datetime import datetime,timezone
CONFIG=re.compile(r'^BOOT-INF/classes/application(?:-[A-Za-z0-9_-]+)?\.(?:yaml|yml|properties)$')
def sha(path):
    digest=hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1024*1024),b''):digest.update(block)
    return digest.hexdigest()
def assemble(current,incoming,staged):
    with zipfile.ZipFile(current) as old,zipfile.ZipFile(incoming) as source,zipfile.ZipFile(staged,'w') as output:
        preserved={i.filename:old.read(i) for i in old.infolist() if CONFIG.match(i.filename)}
        for info in source.infolist():
            if info.filename in preserved:continue
            output.writestr(info,source.read(info))
        for name,data in preserved.items():output.writestr(name,data)
    with zipfile.ZipFile(staged) as check:
        assert check.testzip() is None,'Candidate ZIP integrity failed'
        for name,data in preserved.items():assert check.read(name)==data,'Live configuration changed'
    shutil.copymode(current,staged)
def run(command):subprocess.run(command,check=True,capture_output=True,text=True,timeout=45)
def ready():
    for _ in range(60):
        try:
            with urllib.request.urlopen('http://127.0.0.1:8080/pet-system/pet/list?pageNum=1&pageSize=1',timeout=2) as response:
                if json.load(response).get('code')==200:return True
        except Exception:pass
        time.sleep(1)
    return False
def require_extracted_ai_ready(incoming):
    # The old single-JAR pipeline must never perform the first three-process migration.
    with zipfile.ZipFile(incoming) as archive:
        extracted='BOOT-INF/classes/com/hongjie/pms/bootstrap/ai/AiCoreController.class' in archive.namelist()
    if not extracted:return
    state=subprocess.run(['systemctl','is-active','pms-ai'],capture_output=True,text=True)
    if state.returncode!=0:
        raise RuntimeError('This core requires the coordinated AI extraction rollout; pms-ai is not active. See docs/architecture/ai-service-extraction.md')
    command=subprocess.check_output(['systemctl','show','pms-cloud-gateway','-p','ExecStart','--value'],text=True)
    match=re.search(r'-jar\s+([^;\s}]+)',command)
    if not match:raise RuntimeError('Cannot verify the deployed AI gateway route')
    with zipfile.ZipFile(match[1]) as gateway:
        routes=gateway.read('BOOT-INF/classes/com/hongjie/pms/gateway/GatewayRoutes.class')
    if b'pms-ai-service' not in routes:raise RuntimeError('Deploy the AI gateway route as part of the coordinated first rollout')

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--incoming',required=True,type=Path);parser.add_argument('--sha',required=True);args=parser.parse_args()
    current=Path('/opt/pms/pms.jar');assert sha(args.incoming)==args.sha,'Upload checksum mismatch'
    require_extracted_ai_ready(args.incoming)
    before=sha(current);stamp=datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S')
    stage=current.with_name('pms-stage-'+stamp+'.jar');backup=current.with_name('pms.jar.bak-ci-'+stamp)
    assemble(current,args.incoming,stage);shutil.copy2(current,backup)
    assert sha(current)==before,'Live version changed during staging'
    changed=False
    try:
        run(['sudo','-n','systemctl','stop','pms']);os.replace(stage,current);changed=True
        run(['sudo','-n','systemctl','start','pms']);assert ready(),'Backend readiness failed'
    except BaseException:
        if changed:
            run(['sudo','-n','systemctl','stop','pms']);shutil.copy2(backup,stage);os.replace(stage,current)
        run(['sudo','-n','systemctl','start','pms']);print(json.dumps({'ok':False,'rolledBack':changed,'ready':ready(),'backup':str(backup)}));raise
    print(json.dumps({'ok':True,'sha':sha(current),'backup':str(backup),'preservedLiveConfiguration':True}))
if __name__=='__main__':main()
