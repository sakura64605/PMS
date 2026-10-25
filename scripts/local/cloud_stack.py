"""Local Spring Cloud stack; shared infrastructure stays behind the existing SSH tunnel."""
import argparse,json,os,secrets,shutil,socket,subprocess,sys,time,urllib.request,urllib.error
from pathlib import Path
import yaml
ROOT=Path(__file__).resolve().parents[2]
RUN=ROOT/'target/cloud-validation'
STATE=RUN/'runtime.json'
CLIENT=urllib.request.build_opener(urllib.request.ProxyHandler({}))
PORTS=[8761,8888,8088,8180,8181,8182,5175]
FORWARDS={13306:3306,16379:6379,19200:9200,16333:6333,19876:9876,10911:10911,10909:10909}
def listening(port):
    with socket.socket() as connection:
        connection.settimeout(.3);return connection.connect_ex(('127.0.0.1',port))==0
def read(path):
    with CLIENT.open(path,timeout=3) as response:return json.load(response)
def save(state):
    temporary=STATE.with_suffix('.tmp');temporary.write_text(json.dumps(state,indent=2),encoding='utf-8');temporary.chmod(0o600);temporary.replace(STATE)
def own(entry):
    if os.name=='nt':
        result=subprocess.run(['powershell.exe','-NoProfile','-Command',f"[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new(); Get-CimInstance Win32_Process -Filter 'ProcessId={int(entry['pid'])}' | Select-Object -ExpandProperty CommandLine"],capture_output=True,text=True,encoding="utf-8")
        command=result.stdout
    else:
        try:command=Path('/proc/'+str(entry['pid'])+'/cmdline').read_bytes().decode().replace('\0',' ')
        except OSError:return False
    return entry['marker'].replace('\\','/').casefold() in command.replace('\\','/').casefold()
def stop(entry):
    if not own(entry):return
    if os.name=='nt':subprocess.run(['taskkill','/PID',str(entry['pid']),'/T','/F'],capture_output=True,check=True)
    else:os.kill(entry['pid'],15)
def launch(state,name,args,env,marker):
    flags={'creationflags':subprocess.CREATE_NO_WINDOW} if os.name=='nt' else {'start_new_session':True}
    stdout=(RUN/(name+'.out.log')).open('w',encoding='utf-8');stderr=(RUN/(name+'.err.log')).open('w',encoding='utf-8')
    process=subprocess.Popen(args,cwd=ROOT,env={**os.environ,**env},stdout=stdout,stderr=stderr,**flags);stdout.close();stderr.close()
    entry={'name':name,'pid':process.pid,'marker':str(marker),'args':args,'env':env}
    state['processes'].append(entry);save(state);print(json.dumps({'starting':name,'pid':process.pid}),flush=True);return entry
def wait_ready(entry,url,core=False):
    end=time.monotonic()+100
    while time.monotonic()<end:
        if not own(entry):raise RuntimeError(entry['name']+' exited; inspect '+str(RUN))
        try:
            value=read(url)
            if value.get('code')==200 if core else value.get('status')=='UP':return
        except Exception:pass
        time.sleep(1)
    raise RuntimeError(entry['name']+' readiness timed out; inspect '+str(RUN))
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--skip-build',action='store_true');parser.add_argument('--stop',action='store_true');parser.add_argument('--restart',choices=['search-1','search-2']);parser.add_argument('--stop-service',choices=['search-1','search-2']);args=parser.parse_args()
    RUN.mkdir(parents=True,exist_ok=True)
    if args.stop or args.restart or args.stop_service:
        state=json.loads(STATE.read_text(encoding='utf-8'))
        if args.stop:
            for entry in reversed(state['processes']):stop(entry)
            return
        name=args.restart or args.stop_service;entry=next(row for row in state['processes'] if row['name']==name);stop(entry)
        if args.restart:
            state['processes'].remove(entry);new=launch(state,name,entry['args'],entry['env'],entry['marker']);wait_ready(new,'http://127.0.0.1:'+('8181' if name=='search-1' else '8182')+'/actuator/health')
        return
    occupied=[p for p in PORTS if listening(p)]
    if occupied:raise RuntimeError('Cloud ports already occupied: '+str(occupied))
    if not args.skip_build:
        settings=ROOT/'target/report-build-settings.xml';command=[shutil.which('mvn') or 'mvn']
        if settings.exists():command+=['-s',str(settings)]
        subprocess.run(command+['-DskipTests','package'],cwd=ROOT,check=True)
    config=yaml.safe_load((ROOT/'apps/pms-app/src/main/resources/application.yaml').read_text(encoding='utf-8'))
    jwt=os.environ.get('PMS_GATEWAY_JWT_SECRET') or str(config['jwt']['secret'])
    if jwt.startswith('${') or len(jwt.encode())<32:raise RuntimeError('Provide the actual existing JWT secret for the gateway')
    token=secrets.token_hex(32)
    state={'processes':[],'frontend':'http://127.0.0.1:5175','gateway':'http://127.0.0.1:8088','core':'http://127.0.0.1:8180','registry':'http://127.0.0.1:8761','config':'http://127.0.0.1:8888'}
    save(state)
    java=shutil.which('java');common={'PMS_CONFIG_URL':'http://127.0.0.1:8888','PMS_EUREKA_URL':'http://127.0.0.1:8761/eureka/'}
    def service(name,artifact,port,options=(),extra=None,cloud=False):
        jar=ROOT/artifact
        if not jar.exists():raise RuntimeError('Missing build artifact: '+str(jar))
        options=list(options)
        if cloud:options+=['--spring.profiles.active=cloud']
        command=[java,'-Xms64m','-Xmx'+('768m' if name=='core' else '256m'),'-Dfile.encoding=UTF-8','-Duser.timezone=Asia/Shanghai','-jar',str(jar),'--server.port='+str(port)]+options
        return launch(state,name,command,{**common,**(extra or {})},jar)
    try:
        active=[p for p in FORWARDS if listening(p)]
        if active and len(active)!=len(FORWARDS):raise RuntimeError('Partial existing SSH tunnel; inspect before launching')
        if not active:
            ssh=[shutil.which('ssh'),'-N','-o','BatchMode=yes','-o','ExitOnForwardFailure=yes','-o','ServerAliveInterval=30']
            for local,remote in FORWARDS.items():ssh+=['-L',f'127.0.0.1:{local}:127.0.0.1:{remote}']
            ssh+=['ubuntu@111.229.254.252'];launch(state,'tunnel',ssh,{},'127.0.0.1:13306:127.0.0.1:3306');time.sleep(1)
        registry=service('registry','services/pms-registry/target/pms-registry-0.0.1-SNAPSHOT.jar',8761)
        wait_ready(registry,'http://127.0.0.1:8761/actuator/health')
        config_server=service('config','services/pms-config-server/target/pms-config-server-0.0.1-SNAPSHOT.jar',8888,extra={'PMS_CONFIG_LOCATION':(ROOT/'config/cloud').as_uri()+'/'})
        wait_ready(config_server,'http://127.0.0.1:8888/actuator/health')
        for i,port in enumerate([8181,8182],1):
            entry=service('search-'+str(i),'services/pms-search-service/target/pms-search-service-0.0.1-SNAPSHOT.jar',port,
                ['--pms.instance-id=search-'+str(i),'--pms.search-service.elasticsearch-url=http://127.0.0.1:19200','--spring.elasticsearch.uris=http://127.0.0.1:19200'],{'PMS_SEARCH_SERVICE_TOKEN':token},cloud=True)
            wait_ready(entry,'http://127.0.0.1:'+str(port)+'/actuator/health')
        core=service('core','apps/pms-app/target/PMS-0.0.1-SNAPSHOT.jar',8180,
            ['--spring.profiles.active=local-validation,cloud','--pms.search.mode=cloud','--spring.datasource.url=jdbc:mysql://127.0.0.1:13306/pet_system?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowMultiQueries=true&rewriteBatchedStatements=true','--spring.data.redis.host=127.0.0.1','--spring.data.redis.port=16379','--elasticsearch.host=127.0.0.1','--elasticsearch.port=19200','--spring.elasticsearch.uris=http://127.0.0.1:19200','--rocketmq.name-server=127.0.0.1:19876'],{'PMS_SEARCH_TOKEN':token})
        wait_ready(core,'http://127.0.0.1:8180/pet-system/pet/list?pageSize=1',core=True)
        gateway=service('gateway','services/pms-gateway/target/pms-gateway-0.0.1-SNAPSHOT.jar',8088,extra={'PMS_GATEWAY_JWT_SECRET':jwt},cloud=True)
        wait_ready(gateway,'http://127.0.0.1:8088/actuator/health')
        vite=ROOT/'PMS-react/node_modules/vite/bin/vite.js'
        launch(state,'frontend',[shutil.which('node'),str(vite),'--host','127.0.0.1','--port','5175','--strictPort'],{'PMS_API_TARGET':'http://127.0.0.1:8088'},vite)
        state['ready']=True;save(state)
        print(json.dumps({k:v for k,v in state.items() if k!='processes'}),flush=True)
    except BaseException:
        for entry in reversed(state['processes']):stop(entry)
        raise
if __name__=='__main__':main()
