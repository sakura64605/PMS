# 首批微服务拆分：搜索检索

## 当前范围

Maven reactor 现有父工程、search-contracts、search-engine、pms-search-service、pms-app 五个项目。原业务源码移入 `apps/pms-app/src`，既有 Java 包、启动类和公开接口不变。`PMS-react` 是唯一前端。

```text
React -> pms-app :8080 -> HTTP -> pms-search-service :8081 -> Elasticsearch
             |
             +-> 业务数据库、Redis、MQ、AI 等现有依赖
```

独立服务只负责候选检索，返回总数与有序的 type/id。JWT/账户校验、数据库可见性复查、用户和互动信息补全仍由主应用负责。索引写入与重建调度也仍在主应用；本批没有宣称其他业务或独立数据库已完成拆分。

## 构建和启动

在仓库根目录执行 `mvn package -DskipTests`。单独构建搜索服务可用 `mvn -pl services/pms-search-service -am package -DskipTests`，构建主应用可用 `mvn -pl apps/pms-app -am package -DskipTests`。

启动包：
- `services/pms-search-service/target/pms-search-service-0.0.1-SNAPSHOT.jar`
- `apps/pms-app/target/PMS-0.0.1-SNAPSHOT.jar`

搜索服务环境：`PMS_SEARCH_SERVICE_TOKEN` 至少 32 字符；`PMS_SEARCH_ELASTICSEARCH_URL` 默认为 http://127.0.0.1:9200。服务默认绑定 127.0.0.1:8081，健康检查为 `/actuator/health`。

主应用环境：`PMS_SEARCH_MODE=remote`、`PMS_SEARCH_URL=http://127.0.0.1:8081`，`PMS_SEARCH_TOKEN` 与服务端一致。主应用原有数据库等配置不复制到搜索服务。

完整本地联调：`./scripts/local/start.ps1 -SplitSearch`。脚本生成本次进程间凭据，不写入前端或仓库，沿用已授权 SSH 依赖转发，并关闭本地共享后台任务和 MQ 消费。已有端口占用时先检查旧实例；不传 `-SplitSearch` 时仍按内置检索运行。

## 协议和故障语义

`contracts/search-contracts` 是纯 Java 契约，不依赖 Spring、业务实体或 Mapper。内部入口为 `POST /internal/search/v1/query`，要求 `X-PMS-Service-Token`。只有通过服务鉴权的调用方才能传 actor，不对公网浏览器开放该内部入口。

主应用默认 `pms.search.mode=local`。远程模式只注册 HTTP 后端；服务失败、超时、非 200、非法 JSON 或候选结构均返回原有第三方服务错误，不静默回退，也不伪装成成功的空结果。连接上限 1 秒、请求上限 4 秒。

本地与远程共用 search-engine 的同一份 Elasticsearch DSL，保持审核可见性、类型、排序和分页规则。主应用继续逐项检查数据库状态和归属，ES 不承担最终授权。

## Vue 清理和流水线

`PMS-vue`、`new-pms` 与后端内嵌 Vue 静态产物已删除。源码及旧 deploy.sh 的本地留档在 `backups/legacy-vue-source-20261025.zip`，不入 Git，也不受 Maven clean 影响。旧 deploy.sh 指向历史服务器，已留档后删除。

前端流水线迁至 `PMS-react/Jenkinsfile-frontend`。如果 Jenkins 作业仍使用旧 SCM Script Path，应改为新路径。后端 Jenkinsfile 与本地启动脚本已采用实际多模块 jar 路径；根 target 下的旧 jar 不再作为发布入口。主应用不再打包前端，React 由独立前端流程发布。

## 验证与边界

协议、独立服务真实 HTTP 鉴权、主应用 HTTP 适配器、MySQL 搜索补全、原有查询/索引和架构相关测试通过；补测了后端模式互斥、凭据缺失与超时边界。

已启动两个独立 Java 进程，主应用通过远程检索从真实 Elasticsearch 返回 3 条结果；内部无凭据请求为 401，健康检查正常。搜索服务 jar 不含 MySQL、Redis、RocketMQ、langchain4j，主应用 jar 不含旧 Vue 资源。原有 HTTP/WebSocket 10 组冒烟通过，临时测试数据已清理；React 51 项测试通过。

生产实例本批没有部署或切换模式。下一步应通过稳定事件/查询契约拆索引同步、通知或 AI 查询端口。181 条跨业务内部引用的历史债务仍在，依赖基线继续限制新增内部调用，新增 reactor 本身不代表完整微服务化。

部署模板位于 `ops/systemd/pms-search.service` 和 `ops/systemd/search.env.example`；本次只提供模板，没有安装到服务器。实际部署时将独立搜索 jar 放到 /opt/pms/search/pms-search-service.jar，并在主应用和搜索服务中使用同一份新生成的凭据。\n