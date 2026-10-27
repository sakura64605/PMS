"""Provision the pinned XXL-JOB admin alongside PMS; does not switch PMS scheduling mode."""

import hashlib, json, os, secrets, subprocess, time, urllib.request, zipfile, yaml
from pathlib import Path
from datetime import datetime
from zoneinfo import ZoneInfo

BASE = Path("/opt/pms/xxl-job")
IMAGE = "xuxueli/xxl-job-admin:3.1.0"
NAME = "pms-xxl-job-admin"
SCHEMA_SHA = "6cfbb5cf89b1f1c9eb1690513178f8db280dbb6681a573baab010888b903047e"
BASE.mkdir(parents=True, exist_ok=True)
BASE.chmod(0o700)
state_file = BASE / "credentials.json"
fresh = not state_file.exists()
if fresh:
    state = {
        "adminUser": "admin",
        "adminPassword": secrets.token_urlsafe(18),
        "databasePassword": secrets.token_hex(24),
        "accessToken": secrets.token_hex(32),
    }
    state_file.write_text(json.dumps(state, indent=2))
    state_file.chmod(0o600)
else:
    state = json.loads(state_file.read_text())


def run(args, **kwargs):
    try:
        return subprocess.run(args, check=True, text=True, capture_output=True, **kwargs)
    except subprocess.CalledProcessError as failure:
        detail = failure.stderr or "command failed"
        for secret in state.values():
            detail = detail.replace(secret, "[redacted]")
        raise RuntimeError(detail) from None


def merge(left, right):
    for key, value in right.items():
        if isinstance(value, dict) and isinstance(left.get(key), dict):
            merge(left[key], value)
        else:
            left[key] = value
    return left


runtime = {}
with zipfile.ZipFile("/opt/pms/pms.jar") as jar:
    for name in ["BOOT-INF/classes/application.yaml", "BOOT-INF/classes/application-prod.yaml"]:
        if name in jar.namelist():
            merge(runtime, yaml.safe_load(jar.read(name)) or {})
external = Path("/opt/pms/config/application-prod.yaml")
if external.exists():
    merge(runtime, yaml.safe_load(external.read_text()) or {})
datasource = runtime["spring"]["datasource"]
assert "\n" not in datasource["password"], "Unsupported multiline database credential"


def sql(statement):
    result = run(
        [
            "sudo",
            "-n",
            "docker",
            "exec",
            "-i",
            "pet-mysql",
            "sh",
            "-c",
            "IFS= read -r MYSQL_PWD; export MYSQL_PWD; exec mysql -u"
            + datasource["username"]
            + " --default-character-set=utf8mb4 --batch --skip-column-names",
        ],
        input=datasource["password"] + "\n" + statement,
    )
    return result.stdout.strip()


exists = sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='pms_xxl_job';")
if exists == "0":
    data = (BASE / "schema.sql").read_bytes()
    assert hashlib.sha256(data).hexdigest() == SCHEMA_SHA, "Unexpected admin schema source"
    sql(data.decode().replace("`xxl_job`", "`pms_xxl_job`"))
    password_hash = hashlib.md5(state["adminPassword"].encode()).hexdigest()
    sql(
        "UPDATE pms_xxl_job.xxl_job_user SET password='"
        + password_hash
        + "' WHERE username='admin';"
    )
elif fresh:
    raise RuntimeError(
        "Existing XXL database without matching credentials; manual reconciliation required"
    )
password = state["databasePassword"]
sql(
    "CREATE USER IF NOT EXISTS 'pms_xxl'@'%' IDENTIFIED BY '" + password + "';"
    "ALTER USER 'pms_xxl'@'%' IDENTIFIED BY '" + password + "';"
    "GRANT ALL PRIVILEGES ON pms_xxl_job.* TO 'pms_xxl'@'%';"
)

config = f"""server.address=127.0.0.1
server.port=9090
server.servlet.context-path=/xxl-job-admin
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/pms_xxl_job?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
spring.datasource.username=pms_xxl
spring.datasource.password={password}
spring.datasource.hikari.minimum-idle=1
spring.datasource.hikari.maximum-pool-size=5
xxl.job.accessToken={state['accessToken']}
xxl.job.i18n=zh_CN
xxl.job.logretentiondays=14
"""
admin_config = BASE / "admin.properties"
admin_config.write_text(config)
admin_config.chmod(0o600)
logs = BASE / "admin-logs"
logs.mkdir(exist_ok=True)
containers = run(
    [
        "sudo",
        "-n",
        "docker",
        "ps",
        "-a",
        "--filter",
        "name=^/" + NAME + "$",
        "--format",
        "{{.Names}}",
    ]
).stdout.strip()
if containers:
    owner = run(
        [
            "sudo",
            "-n",
            "docker",
            "inspect",
            NAME,
            "--format",
            '{{index .Config.Labels "com.pms.component"}}',
        ]
    ).stdout.strip()
    assert owner == "xxl-job-admin", "Container name is owned by another deployment"
    run(["sudo", "-n", "docker", "start", NAME])
else:
    run(
        [
            "sudo",
            "-n",
            "docker",
            "run",
            "-d",
            "--name",
            NAME,
            "--label",
            "com.pms.component=xxl-job-admin",
            "--restart",
            "unless-stopped",
            "--network",
            "host",
            "--memory",
            "512m",
            "--memory-swap",
            "1g",
            "--log-opt",
            "max-size=10m",
            "--log-opt",
            "max-file=3",
            "-e",
            "TZ=Asia/Shanghai",
            "-v",
            str(admin_config) + ":/config/admin.properties:ro",
            "-v",
            str(logs) + ":/data/applogs",
            "--entrypoint",
            "java",
            IMAGE,
            "-Xms64m",
            "-Xmx192m",
            "-XX:MaxMetaspaceSize=128m",
            "-Duser.timezone=Asia/Shanghai",
            "-jar",
            "/app.jar",
            "--spring.config.additional-location=file:/config/admin.properties",
        ]
    )

ready = False
for attempt in range(90):
    try:
        with urllib.request.urlopen(
            "http://127.0.0.1:9090/xxl-job-admin/toLogin", timeout=3
        ) as response:
            if response.status == 200:
                ready = True
                break
    except Exception:
        pass
    time.sleep(2)
assert ready, "XXL-JOB admin readiness timed out; PMS was not switched"

groups = sql("SELECT id FROM pms_xxl_job.xxl_job_group WHERE app_name='pms-executor';").splitlines()
assert len(groups) <= 1, "Duplicate PMS executor groups"
if not groups:
    sql(
        "INSERT INTO pms_xxl_job.xxl_job_group(app_name,title,address_type,address_list,update_time)"
        " VALUES('pms-executor','PMS任务执行器',0,NULL,NOW());"
    )
group = int(sql("SELECT id FROM pms_xxl_job.xxl_job_group WHERE app_name='pms-executor';"))
definitions = [
    ("pmsDueTasks", "到期业务任务", "0/15 * * * * ?"),
    ("pmsActivityStatus", "活动状态同步", "0 * * * * ?"),
    ("pmsInboxCleanup", "已读收件箱清理", "0 0 2 * * ?"),
    ("pmsDailyStatistics", "每日统计", "0 0 1 * * ?"),
    ("pmsStatisticsCompensation", "统计补偿", "0 0 3,6,9,12,15,18,21 * * ?"),
]
job_ids = {}
for handler, title, cron in definitions:
    ids = sql(
        f"SELECT id FROM pms_xxl_job.xxl_job_info WHERE job_group={group} AND executor_handler='{handler}';"
    ).splitlines()
    assert len(ids) <= 1, "Duplicate handler definition: " + handler
    if not ids:
        sql(
            f"""INSERT INTO pms_xxl_job.xxl_job_info
(job_group,job_desc,add_time,update_time,author,alarm_email,schedule_type,schedule_conf,misfire_strategy,
executor_route_strategy,executor_handler,executor_param,executor_block_strategy,executor_timeout,
executor_fail_retry_count,glue_type,glue_source,glue_remark,glue_updatetime,child_jobid,trigger_status)
VALUES({group},'{title}',NOW(),NOW(),'PMS','','CRON','{cron}','DO_NOTHING','FIRST','{handler}','','DISCARD_LATER',0,1,'BEAN','','PMS migration',NOW(),'',0);"""
        )
    job_ids[handler] = int(
        sql(
            f"SELECT id FROM pms_xxl_job.xxl_job_info WHERE job_group={group} AND executor_handler='{handler}';"
        )
    )

executor_env = BASE / "executor.env"
executor_env.write_text(
    f"""PMS_SCHEDULING_MODE=xxl
PMS_SCHEDULING_XXL_ADMIN_ADDRESSES=http://127.0.0.1:9090/xxl-job-admin
PMS_SCHEDULING_XXL_ACCESS_TOKEN={state['accessToken']}
PMS_SCHEDULING_XXL_APPNAME=pms-executor
PMS_SCHEDULING_XXL_IP=127.0.0.1
PMS_SCHEDULING_XXL_PORT=9999
PMS_SCHEDULING_XXL_LOGPATH=/opt/pms/xxl-job/executor-logs
TZ=Asia/Shanghai
"""
)
executor_env.chmod(0o600)

nginx = Path("/etc/nginx/conf.d/pms.conf")
original = nginx.read_text()
nginx_backup = None
if "location ^~ /xxl-job-admin/" not in original:
    stamp = datetime.now(ZoneInfo("Asia/Shanghai")).strftime("%Y%m%d-%H%M%S")
    nginx_backup = str(BASE / ("pms.nginx.bak-" + stamp))
    Path(nginx_backup).write_text(original)
    route = """    location = /xxl-job-admin { return 302 /xxl-job-admin/; }
    location ^~ /xxl-job-admin/ {
        proxy_pass http://127.0.0.1:9090;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

"""
    assert "    location / {" in original, "Unexpected nginx layout"
    candidate = BASE / "pms.nginx.next"
    candidate.write_text(original.replace("    location / {", route + "    location / {", 1))
    run(["sudo", "-n", "install", "-m", "644", str(candidate), str(nginx)])
    try:
        run(["sudo", "-n", "nginx", "-t"])
        run(["sudo", "-n", "systemctl", "reload", "nginx"])
    except Exception:
        run(["sudo", "-n", "install", "-m", "644", nginx_backup, str(nginx)])
        run(["sudo", "-n", "nginx", "-t"])
        raise
metadata = {
    "adminUrl": "http://111.229.254.252/xxl-job-admin/",
    "image": IMAGE,
    "database": "pms_xxl_job",
    "groupId": group,
    "jobIds": job_ids,
    "nginxBackup": nginx_backup,
    "pmsSchedulingSwitched": False,
}
(BASE / "admin-meta.json").write_text(json.dumps(metadata, indent=2))
print(json.dumps(metadata))
