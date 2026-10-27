"""Generate private AMap deployment files; never print credentials or modify production.

Outputs belong in an ignored directory. JS API key is public; its security code
stays in a root-readable Nginx include, not Core or the React build.
"""

import argparse
import os
import re
from pathlib import Path


def prepare(source: Path, output: Path):
    if any(part in {"target", "dist", "node_modules"} for part in source.resolve().parts + output.resolve().parts):
        raise ValueError("私有凭据和生成文件不能放在构建清理目录，请使用 .local/location")
    values = {}
    for line in source.read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        name, separator, value = line.partition("=")
        if not separator or name not in {"PMS_AMAP_JS_API_KEY", "PMS_AMAP_JS_SECURITY_CODE"}:
            raise ValueError("配置文件仅允许两项高德 Web端凭据")
        if name in values:
            raise ValueError("存在重复配置项")
        if not re.fullmatch(r"[A-Za-z0-9_-]{16,128}", value) or value.startswith("REPLACE_"):
            raise ValueError("请填写有效高德凭据；不接受占位符、空格或控制字符")
        values[name] = value
    if len(values) != 2:
        raise ValueError("缺少 Web端 Key 或对应安全密钥")
    output.mkdir(parents=True, exist_ok=True)
    os.chmod(output, 0o700)
    key = values["PMS_AMAP_JS_API_KEY"]
    security = values["PMS_AMAP_JS_SECURITY_CODE"]
    files = {
        "core-amap.env": f"PMS_AMAP_ENABLED=true\nPMS_AMAP_JS_API_KEY={key}\n",
        "00-pms-amap-rate-limit.conf": "limit_req_zone $binary_remote_addr zone=pms_amap:1m rate=5r/s;\n",
        "pms-amap.enabled.conf": f"""# Private server-level include. Do not commit or print with nginx -T.
location /_AMapService/ {{
    limit_except GET {{ deny all; }}
    if ($arg_key != "{key}") {{ return 403; }}
    if ($uri !~ "^/_AMapService/v3/(place/(text|around)|geocode/regeo|assistant/(inputtips|coordinate/convert)|coordinate/convert)$") {{ return 404; }}
    limit_req zone=pms_amap burst=10 nodelay;
    limit_req_status 429;
    set $args "$args&jscode={security}";
    proxy_pass https://restapi.amap.com/;
    proxy_ssl_server_name on;
    proxy_ssl_name restapi.amap.com;
    proxy_ssl_verify on;
    proxy_ssl_trusted_certificate /etc/ssl/certs/ca-certificates.crt;
    proxy_set_header Host restapi.amap.com;
    proxy_set_header Authorization "";
    proxy_set_header Cookie "";
    proxy_hide_header Set-Cookie;
    proxy_connect_timeout 3s;
    proxy_read_timeout 8s;
    proxy_send_timeout 8s;
    access_log off;
    # Normal upstream errors can include the URI containing jscode.
    error_log /var/log/nginx/pms-amap-error.log crit;
}}
""",
    }
    for name, text in files.items():
        path = output / name
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
            stream.write(text)
        os.chmod(path, 0o600)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.env_file, args.output_dir)
    print("高德配置文件已生成；未连接服务器、未部署，凭据未输出。")


if __name__ == "__main__":
    main()
