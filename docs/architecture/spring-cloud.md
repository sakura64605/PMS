# Spring Cloud 服务化

本阶段以高用户量下的独立扩容、故障隔离和数据归属为目标。使用 Java 17、Spring Boot 3.5.16、Spring Cloud 2025.0.3；MyBatis-Plus 升至 3.5.17。版本依据：[Cloud 兼容矩阵](https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions)。

## 已落地的进程边界

| 应用 | 职责 | 本地端口 |
|---|---|---|
| pms-gateway | 公开 API / WebSocket 路由、JWT 签名与有效期校验、清除伪造内部请求头 | 8088 |
| pms-registry | Eureka 服务注册发现 | 8761 |
| pms-config-server | 提供非敏感配置、超时和熔断策略 | 8888 |
| pms-core-service | 现有业务、权限与归属校验、数据库事务、搜索结果装配和索引同步 | 8180 |
| pms-search-service | 独立 Elasticsearch 检索，通过版本化协议返回候选 ID | 8181、8182 |

目前是两个业务应用和三个治理应用。两个搜索实例属于同一服务。核心业务尚未全部分离，搜索索引写入仍由核心应用负责。

调用链：React → Gateway → Core → OpenFeign → LoadBalancer → Search → Elasticsearch。Eureka 发现实例，Config 提供策略，Resilience4j 保护搜索调用；Micrometer / OpenTelemetry 传播 traceId。网关保留原 `/pet-system/...` 路径，核心应用继续校验账户状态、角色和资源归属。

搜索是只读 POST，允许在连接失败时换另一个实例重试一次；不重试同一实例，不对普通写请求启用重试。失败返回原有业务错误，不用空结果掩盖依赖故障。内部检索还要求进程间凭据，不通过网关公开。

## 目标业务边界与拆分顺序

| 服务 | 数据归属与职责 | 拆分时必须解决的问题 |
|---|---|---|
| 身份与账户 | 用户、认证、封禁、密码版本 | 统一令牌协议和账户变更事件，禁止其他服务直写用户表 |
| 内容 | 领养、救助、日常、分类与话题 | 发布状态与审核结果事件，媒体归属 |
| 活动 | 活动、报名、名额与签到 | 名额在活动本地事务内扣减，报名唯一约束 |
| 社区互动 | 点赞、收藏、评论、关注 | 唯一约束、计数事件幂等、可见性校验 |
| 消息 | 私信、会话、通知、实时连接 | 持久化后推送、多节点连接路由、重复投递去重 |
| 搜索 | 搜索投影与检索 | 最终接管索引消费、重放和重建，避免跨服务扫表 |
| AI | 会话、用户记忆、知识库、模型编排 | 通过服务 API 查询业务，隔离模型超时与配额 |
| 社区治理 | 审核、举报、处罚、公告 | 用业务命令和事件驱动处置，禁止跨库修改业务状态 |
| 分析与任务 | 统计投影、XXL-JOB 执行器 | 事件增量统计、执行幂等和可重放 |

这些是后续边界设计，尚未全部实现。无需每个 Controller 或数据表独立部署。报名与活动名额属于同一事务边界；点赞与其去重记录也应由同一服务拥有。

后续每次抽取一个完整业务闭环：先声明契约和表归属，再解除跨模块 Mapper 访问，然后独立部署。跨服务写入采用本地事务 + Outbox 可靠投递、消费者去重与补偿；这些机制需要随业务抽取实现，本阶段不宣称已具备分布式事务。共享 contracts 只容纳协议，不共享业务实体和数据库 Mapper。

## 本地启动

```powershell
mvn package -DskipTests
python scripts/local/cloud_stack.py --skip-build
# 停止 / 恢复一个搜索实例，以观察故障处理
python scripts/local/cloud_stack.py --stop-service search-1
python scripts/local/cloud_stack.py --restart search-1
# 只停止本脚本记录且身份匹配的进程
python scripts/local/cloud_stack.py --stop
```

脚本需要 Python、PyYAML、Java、Node 和已配置的 SSH 密钥；通过 SSH 隧道访问已有服务器依赖。前端地址为 http://127.0.0.1:5175。启动前确认本地真实配置存在；JWT 密钥沿用核心应用，服务凭据每次生成，不提交仓库。`target/cloud-validation/runtime.json` 包含本地进程私有环境，不能分享或入库。

独立运行时使用 `cloud` profile，并提供 `PMS_CONFIG_URL`、`PMS_EUREKA_URL`；网关需要 `PMS_GATEWAY_JWT_SECRET`，核心需要 `PMS_SEARCH_TOKEN`，搜索需要相同值的 `PMS_SEARCH_SERVICE_TOKEN`。非 Cloud 模式仍支持现有内置/固定地址检索。

本地治理服务仅监听 loopback。短租约和快速刷新用于故障演示；生产需要重新配置租约、注册地址、网络隔离、配置鉴权和注册中心高可用。当前单节点 Eureka、native Config 不能称为高可用集群。尚未部署分布式 trace 收集器，当前通过请求头和日志核对 traceId。

## 简历表述边界

可据实描述：基于 Spring Cloud Gateway、Eureka、Config 构建服务治理，抽取独立搜索服务，以 OpenFeign 和客户端负载均衡调用，接入 Resilience4j 超时熔断与 Micrometer 链路标识，并用双实例故障注入验证恢复。

不能将本地功能验证写成生产高并发指标；用户量、QPS、可用性和延迟改善都需要实际压测或监控证据。也不能把规划中的服务数量写成已完成数量。

## 本阶段验证记录（2026-10-25）

本地完整启动了五类应用，搜索启动两个实例。`scripts/local/verify_cloud.py` 的真实 HTTP 验证结果：注册中心可见两个搜索实例，20 次搜索分别由两个实例处理各 10 次，响应一致，核心响应 traceId 可在搜索日志对应。停止一个搜索实例后 8/8 次查询成功；停止全部搜索实例后，搜索返回错误、`searchCandidates` 熔断器进入 OPEN，普通宠物列表仍成功。恢复两个实例后搜索自动恢复。

该脚本仅执行读取请求及本地搜索进程停止/恢复，不修改线上部署或索引。它是功能和故障注入验证，不是吞吐量压测。运行证据保存在本地忽略目录 `target/cloud-validation/verification.json`。

此外，通过新网关完成登录和 8 个核心读取接口检查，验证 AI 会话、反馈、归属隔离、转人工幂等与真实 WebSocket；临时测试账户和会话记录已清理。框架升级回归中发现的 MyBatis-Plus 测试初始化错误已修正，失败用例及新增搜索适配器用例复测通过。网关 JWT、管理端隔离和检索服务 HTTP 测试均通过。2026-10-26 已完成[线上发布与验证](../deployment/spring-cloud-20261026.md)。
