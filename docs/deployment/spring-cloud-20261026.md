# 2026-10-26 Spring Cloud 发布记录

访问地址：http://111.229.254.252 。公开接口路径不变。

发布目录：`/opt/pms/releases/cloud-6513855-20261026`。
后端与网关发布源版本：`142e70a`（包含 `cda4df3` 的 Cloud 接入与 SockJS 修复）。
线上主 JAR SHA-256：`0de225737d8ee388f36acdf03a3afefa5938b5294a301508a542ecaa877cd167`。

## 运行结构

| systemd 服务 | 监听端口 | 职责 |
|---|---|---|
| pms | 127.0.0.1:8080 | 核心业务，prod + cloud，继续执行现有 XXL-JOB |
| pms-cloud-gateway | 127.0.0.1:8088 | API、原生 WebSocket、SockJS 路由与 JWT 校验 |
| pms-cloud-registry | 127.0.0.1:8761 | Eureka |
| pms-cloud-config | 127.0.0.1:8888 | 非敏感配置 |
| pms-cloud-search-1 | 127.0.0.1:8181 | 搜索检索实例 1 |
| pms-cloud-search-2 | 127.0.0.1:8182 | 搜索检索实例 2 |

Nginx 的三个 API / WebSocket 转发位置从 8080 切到 8088。注册、配置、搜索和核心进程不直接向公网开放。前端仍位于 `/var/www/html/pms`，兼容现有 Jenkins 前端流水线，先发布静态资源再原子替换入口，保留旧散列资源。

服务器约 4GB 内存，新增服务限制 JVM 堆与处理器数量；注册 / 配置 / 搜索 / 网关的最大堆分别为 128 / 96 / 128（每实例）/ 160MB。核心最大堆仍为 1024MB，初始堆降为 128MB。此为同机演示部署，不是高可用集群或高并发容量证明。没有停止或改动其他已有基础设施。

## 配置与回滚保护

主应用的服务器配置从原 JAR 保留，上传的核心包排除了开发机 `application.yaml`。XXL-JOB 原有环境文件和 drop-in 保留；Cloud 另设 `/etc/systemd/system/pms.service.d/cloud.conf`。服务凭据只保存在发布目录的受限 `env/`，不入库、不输出。

首次尝试发现旧服务器配置把注册名覆盖为 `PMS`，在 Nginx 切换前受控回滚。最终用命令行明确指定 `spring.application.name=pms-core-service`，同时确保读取正确的配置中心应用配置。原 JAR 的 SHA 在回滚后核对一致，之后重新发布成功。

发布前备份：

- `backend-before.jar`：原主应用。
- `nginx-before.conf`：原 Nginx 配置。
- `frontend-index-before.html`：原前端入口。
- `current-before.json`：原发布指针。

本次部署脚本适用于首次 Cloud 安装，会拒绝覆盖已有 Cloud units。后续更新需按服务分别替换发布包，不能直接重复首次安装流程，也不要使用首次 XXL 安装脚本。

需要撤销本次 Cloud 切换时，在服务器执行：

```bash
python3 /opt/pms/releases/cloud-6513855-20261026/deploy_cloud.py \
  --release /opt/pms/releases/cloud-6513855-20261026 --rollback
```

该命令恢复原入口、Nginx 和主 JAR，移除新增 Cloud units / drop-in，并恢复原发布指针；不修改数据库结构或 XXL-JOB 配置。

## 验证结果

- 29 项核心检查：旧令牌兼容、账户资料缓存、列表 / Feed / 私信通知读取、审核与统计、发布审核、搜索增量同步、点赞取消、评论计数、删除可见性和密码 / 退出令牌失效。
- 10 组公网 AI 检查：会话、消息落库、反馈、归属隔离、记忆缓存、转人工幂等、删除与原生 WebSocket。
- 通知 SockJS 的 `/info` HTTP 握手与公网 WebSocket 连接均成功。
- 两个搜索实例都注册并处理了真实网关请求；伪造身份头、无效令牌被拒绝，内部检索受到保护。
- 五个 XXL-JOB 新回调成功，handleCode 均为 200：任务、活动状态、收件箱清理、日统计、统计补偿。
- 线上核心日志检查未发现新增 ERROR，服务未发生自动重启，Nginx 配置检查成功；React 首页及三个入口静态资源可访问。
- 临时测试账户、帖子和 AI 会话均已清理。没有在生产上执行搜索实例故障注入；故障隔离 / 熔断 / 恢复验证已在本地完成。

详细结果位于发布目录 `core-verification.json`、`cloud-verification.json`、`jobs-verification.json`。主发布记录为 `deployment.json` 与 `/opt/pms/releases/current.json`。这些记录不含访问凭据。
