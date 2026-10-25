# 核心业务组合验证与发布（2026-10-25）

## 发布结果

已于 2026-10-25 12:30（Asia/Shanghai）完成部署，随后检查通过。
服务器：111.229.254.252；服务：pms；部署路径：/opt/pms/pms.jar。
代码版本：b97f2a9，覆盖最近用户、日常、统计、搜索查询与索引同步几批重构。
本次只发布后端；保留线上应用配置、嵌入静态资源、XXL-JOB drop-in、
执行器环境文件和网络保护规则，没有发布前端或修改 nginx。

构建包 SHA-256：
3b11d6aae412eeaf4411a33041247b22dffb9f4232e5816936730e7376a09cbe

实际部署包 SHA-256（保留线上配置和静态资源后）：
e25d8005cf283ffd5bc97f2ceb60776a659c482664001d80bd5ac4b02609dc7c

原包 SHA-256：
1de6245ed93e8c74a7a67d93f518bc7eb53841843ff79e5b42eb90d5c872c0fa

原包备份：/opt/pms/pms.jar.bak-core-20261025-122913。
部署记录：/opt/pms/releases/core-b97f2a9-20261025/deployment.json。
最终检查：同目录 verification-final.json；当前发布指针：/opt/pms/releases/current.json。
这些记录不包含登录密码或 JWT。

## 部署前集成验证

216 个相关测试通过，0 失败、0 错误、0 跳过。
覆盖用户、日常、统计、搜索及相关审核、点赞、调度、缓存装配，
真实 MySQL 测试使用本机 pet_system_test，未运行无关模块的全量测试。

选择器：
mvn -s target/report-build-settings.xml "-Dtest=*User*Test,*Daily*Test,*Statistics*Test,*Search*Test,ElasticsearchIndexStoreTest,*Audit*Test,*Like*Test,SchedulingConfigurationTest,ScheduledTaskMySqlTest,PmsJobActionsTest,CacheWiringTest" test

同时核对线上必要字段和统计日期唯一索引；部署前登录及核心读取接口均正常。
依赖库无变更。部署使用 286826 字节差异包，共 97 个变更条目；
在服务器流式还原完整 jar，并逐条验证所有构建条目 SHA-256，
另行验证保留的线上配置、静态资源。没有直接拿本机配置覆盖线上配置。

## 激活过程

保存原调度启用状态，暂时停止新触发并等待正在执行的任务回调。
备份旧包后切换 jar、重启 pms，验证完整应用在真实 MySQL、Redis、MQ、
Elasticsearch、Qdrant 环境下启动及接口可用，再进行业务写入检查。
配置与调度文件摘要在切换前后一致。

原定失败条件会恢复旧 jar、原搜索别名及原调度状态；本次未触发回滚。
5 个任务的手动执行回调全部成功后，恢复原有启用状态。

## 线上核心链路

29 项检查全部通过，包括：
- 部署前签发的旧管理员令牌仍兼容，新登录及当前用户信息正常。
- 领养/救助、活动、日常推荐、热门话题、公告、Feed 的读取。
- 消息中心、私信会话及未读数量。
- 审核待办、审核历史。
- 实时、日报、周报、月报、年报、区间统计。
- 全局搜索与重建状态。
- 临时账号注册、更新资料后立即读取新值。
- 日常发布：客户端伪造作者/审核字段未被采用，服务端仍建立本人待审内容。
- 审核通过后真实 ES 搜索可召回该帖子。
- 点赞、取消点赞、评论及当前计数。
- 删除后搜索不再返回该内容。
- 修改密码使旧令牌失效，新登录后退出使当前令牌失效。

仅对本次隔离账号和内容进行写操作。测试结束后，
对应账号、帖子、评论、审核、消息、行为与兴趣等数据按归属清理；
最终再次确认临时账号、帖子及 ES 文档均为零。
探测用的旧管理员令牌已退出并删除服务器临时令牌文件。

## 搜索及 XXL-JOB

真实 ES 重建扫描 165 条业务记录，状态 SUCCEEDED，最终 incrementalFailures=0。
新别名 pms_unified_search 指向：
pms_search_707778c1f6ab4a0eba9667ea6d1d711e。

原 unified_search 物理索引仍保留，没有删除在线旧索引再回填。
上线前集群为单节点 yellow，原因是旧索引副本未分配，主分片正常；
本轮未调整旧索引副本或更改集群配置。

5 个 XXL handler 手动触发及执行回调均为 200：
- pmsDueTasks：job 4，验证日志 4003。
- pmsActivityStatus：job 5，验证日志 4004。
- pmsInboxCleanup：job 6，验证日志 4005。
- pmsDailyStatistics：job 7，验证日志 4006。
- pmsStatisticsCompensation：job 8，验证日志 4007。

恢复定时调度后，再观察到 pmsDueTasks 的日志 4024、
pmsActivityStatus 的日志 4021，均回调成功；
其余三个任务的最近执行也成功，五个任务均保持启用。

## 最终观察与边界

pms 为 active；本次启动及检查期间没有新增 ERROR 日志。
公网页面及 API 可访问；排除本机代理后，抽查页面和宠物列表直连均约 125ms，
这是一次访问样本，不是性能压测结论。
服务器本地核心读取样本约 14～136ms，搜索样本约 952ms。

本次确认的是组合测试、完整启动、核心 HTTP 写入链路、真实 ES 和调度连通性；
未进行长时间压力测试或逐页人工 UI 验收。
AI 模块未在本轮重构。搜索持久化重试队列仍未实现，
原有历史统计可还原性等限制继续以各模块架构说明为准。
