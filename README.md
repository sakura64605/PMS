# PetCircle · 宠友社（PMS）

宠物领养/救助、活动、社区互动、私信通知和 AI 养宠顾问。前端只保留 React + TypeScript + Vite；后端使用 Java 17、Spring Boot 3.5.16 / Spring Cloud 2025.0.3，正按业务边界逐步拆分服务。

## 当前工程

```text
pom.xml                         Maven 父工程与 reactor
apps/pms-app/                   现有业务应用，公开接口仍由它提供
services/pms-gateway/           Spring Cloud Gateway
services/pms-registry/          Eureka
services/pms-config-server/     Config Server
config/cloud/                  non-secret Cloud configuration
services/pms-search-service/    首个独立服务：Elasticsearch 搜索检索
contracts/search-contracts/     无 Spring/数据库依赖的 v1 搜索协议
components/search-engine/      两种运行模式共用的搜索 DSL/执行代码
PMS-react/                     唯一前端工程
scripts/local/                 本地启动脚本
ops/                           Nginx 等运行配置片段
database/                      人工 SQL、基线与种子资料，不自动执行
docs/architecture/             模块边界、依赖基线及拆分说明
```

业务源码现在位于 `apps/pms-app/src/`。原 `PetSystemApplication` 启动类和 `/pet-system/...` 公开接口保持不变；并不是所有业务都已经成为独立微服务。

## Spring Cloud

```powershell
python scripts/local/cloud_stack.py --skip-build
python scripts/local/verify_cloud.py
```

React: http://127.0.0.1:5175; Gateway: http://127.0.0.1:8088.

[Spring Cloud architecture and service boundaries](docs/architecture/spring-cloud.md).

## 构建

需要 Java 17、Maven；前端需要 Node.js 24+。

```bash
mvn package -DskipTests
# 只构建搜索服务及其依赖
mvn -pl services/pms-search-service -am package -DskipTests
# 只构建现有业务应用及其依赖
mvn -pl apps/pms-app -am package -DskipTests
```

启动包：
- `apps/pms-app/target/PMS-0.0.1-SNAPSHOT.jar`
- `services/pms-search-service/target/pms-search-service-0.0.1-SNAPSHOT.jar`

不再使用根目录 `target/PMS-0.0.1-SNAPSHOT.jar`。首次从源码启动主应用时，参考 `apps/pms-app/src/main/resources/application-example.yaml` 提供自己的运行配置；真实 `application.yaml` 不入库。

## 本地双进程联调

PowerShell：

```powershell
./scripts/local/start.ps1 -SplitSearch
```

脚本按本项目已有授权方式，通过 SSH 使用服务器 MySQL/Redis/Elasticsearch 等依赖，并关闭本地实例的共享后台任务和 MQ 消费。它会生成本次进程间凭据，不写进前端或仓库。占用端口时会拒绝启动，先检查原有实例。

- React：http://127.0.0.1:5174
- 主应用：http://127.0.0.1:8080
- 搜索服务健康检查：http://127.0.0.1:8081/actuator/health

不传 `-SplitSearch` 时，主应用仍使用内置搜索实现，便于渐进切换。独立运行和配置说明见 [首批服务拆分](docs/architecture/search-service-extraction.md)。

## 前端

```bash
cd PMS-react
npm ci
npm test
npm run build
npm run dev
```

前端流水线文件已移到 `PMS-react/Jenkinsfile-frontend`。如果 Jenkins 作业仍使用旧的脚本路径，需要将 SCM Script Path 改成这个路径。旧 `PMS-vue`、`new-pms` 和后端内嵌 Vue 资源已删除；本地留档在忽略入库的 `backups/legacy-vue-source-20261025.zip`。

模块约束检查：`python scripts/architecture/check_module_dependencies.py`。
