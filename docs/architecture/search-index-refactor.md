# 搜索索引同步与安全重建

## 接入范围

沿用全部实体、业务表和旧服务实现，新增 index 子包：
PmsSearchDataSyncService、PmsFullDataSyncService 使用 @Primary，覆盖旧类全部公开业务方法。
旧实体入参只提取业务 ID；topicIds、正文、计数等事件快照不作为写入来源。
null 发布对象安全忽略。兼容 deleteById 实际按 ID 对账：记录还存在则更新，
不存在/已删除才删除文档，避免旧删除事件删掉后来恢复的内容。
显式 deleteIndex 不再允许删除在线索引，改用原 rebuild 入口。

旧 FullDataSyncService 和 ElasticsearchIndexInitializer 的启动监听条件设为 false，
只调整装配注解，保留原业务方法；由 SearchIndexBootstrap 提供唯一启动流程。
SearchDataSyncListener 是共享适配层：取消提前异步执行，转交提交后派发器。
SearchController 保留请求路径和原 rebuild 响应文本，只替换重建调度并增加状态接口。

## 设计与职责

- SearchIndexSource / SearchIndexSources：宠物、活动、日常的索引投影策略。
- SearchIndexCoordinator：初始化、增量对账、按类型补同步、全量构建与锁顺序。
- SearchIndexStore：ES 存储端口。
- ElasticsearchIndexStore：统一映射、bulk 失败项检查、refresh 和别名切换。
- SearchSyncDispatch：提交后调度，隔离已提交业务与异步失败。
- SearchIndexDomainListener：点赞/取消点赞、评论计数、报名/签到事件同步。
- SearchMaintenance：重建运行状态、重复请求保护和任务提交。
- SearchIndexExecutors：独立、有界的增量与维护线程池。

映射保留原字段与 IK 分词，补齐一致的日期格式；
新索引采用单分片、零副本，适用于当前单节点部署。
没有新增数据库表、配置密钥或依赖版本。

## 搜索别名及初始化

新读写入口统一使用 pms_unified_search 别名。
首次初始化如发现原 unified_search 物理索引，直接把新别名指向它，
保留原索引和原名称；已有新别名则沿用其当前指向。
无旧索引时创建独立物理索引，再建立别名。
新物理索引名称为 pms_search_ 加随机标识，避免并发/重试名称冲突。

启动只提交一次安全重建任务，后台先建立别名、再构建新版本；
首次启动的短暂初始化窗口内，搜索可能返回服务不可用。
ES 不可用时重建状态为失败，不让 ES 失败把整个业务应用启动拉垮。
本次未上线，首次迁移行为尚未在真实服务器集群验证。

## 提交与增量并发

共享搜索监听器在业务线程接收事件，派发器检查事务：
事务中仅注册 afterCommit 回调，回滚不投递；无事务则直接提交异步任务。
因此不会在异步线程中错过原事务上下文、提前读到尚未提交的记录。

增量同步依次取得：
1. pms:search:index 分布式读锁。
2. pms:search:index:类型:ID 分布式文档锁。
3. 从数据库重新读取当前实体、作者和启用话题，再写入当前别名。
4. 逆序释放锁。

所有锁使用 Redisson 看门狗，无固定短租约。
同一文档的读库与写 ES 串行，旧任务晚执行时仍读取最新状态。
不同文档允许并发。已删除内容生成删除操作，待审内容保留审核标记供作者搜索；
查询链路仍会回查数据库做最终可见性校验。

已接入的内容/审核/举报处理事件沿用现有发布入口；
新增点赞、取消点赞、自评/普通评论及报名/签到事件接入，不依赖通知收件人。
浏览、取消报名等没有对应搜索事件的路径并未在本批逐个补发；
这些字段仍在结果装配时读取数据库，ES 排序字段通过后续事件或重建更新。

## 重建流程与失败

取得同一个分布式写锁后：
1. 创建独立的新物理索引，在线别名继续指向原索引。
2. 各类型先取当前最大 ID，再按 ID 游标、每批最多 100 条扫描。
3. 每批批量读取当前实体、作者及话题，不逐条查询作者。
4. 检查 bulk 的 errors，任一失败项即中止；refresh 分片失败也中止。
5. 所有类型成功后，在一次 updateAliases 请求中移除旧别名绑定并添加新绑定。
6. 释放写锁，等待中的增量任务重新读数据库并写入切换后的别名。

正常扫描、bulk、refresh 失败不会切换别名。网络结果不确定或未确认的别名切换
标为失败并要求核对集群实际指向；不会把这种情况报告为确定成功。
旧物理索引始终保留。失败候选索引也保留供排查，本批不自动清理历史索引。
保留的旧索引是切换时的快照，并非后续继续更新的副本。

单机重建请求用运行标记去重，多实例重建通过同一分布式写锁串行。
重建会暂缓 ES 增量写入，不等待它的业务 HTTP 操作仍可以提交。

## 状态与明确边界

管理员操作：
- POST /pet-system/search/rebuild：原路径，提交后台任务。
- GET /pet-system/search/rebuild/status：新增，只允许管理员。

状态字段：phase（IDLE/RUNNING/SUCCEEDED/FAILED）、processed（扫描 ID 数）、
startedAt、finishedAt、error、incrementalFailures。
错误响应不暴露底层异常细节，详细失败写入服务日志。
状态与失败计数在本实例内存中，重启清零，不是跨实例任务历史。

增量线程池 2～4 线程、队列 256；维护池 1 线程、队列 1。
拒绝入队或执行失败计数并记录类型/ID，不反向破坏已提交业务。
尚未实现持久化增量重试队列；进程退出、队列满或同步失败后需要重建补偿。
这不是“所有数据库改动到 ES 都可靠送达”的承诺。
保留上一批的 total/排序来自 ES、数据库复核后页面可能不足条数的边界。

## 验证与交付（2026-10-25）

59 个相关测试全部通过，0 失败、0 错误、0 跳过；未跑全量测试。
移除控制器未使用的旧依赖后，仅复测 9 个装配/HTTP 测试，全部通过；去重仍为 59 个。
选择器：
mvn -s target/report-build-settings.xml "-Dtest=*Search*Test,ElasticsearchIndexStoreTest,LikeChangedListenerTest,PmsLikeServiceTest" test

覆盖：提交后派发与回滚、队列拒绝、失败状态及重试请求、同文档写入顺序、
重建与增量互斥、bulk/refresh 失败保留旧别名、游标扫描和一次别名切换、
旧入口 @Primary 接管、唯一启动流程、原路径及新状态接口权限。
并发协调测试使用真实 JVM 读写锁模拟 Redisson 锁接口；
ES SDK 请求/错误处理使用替身，没有访问线上 ES 或执行真实别名切换。
3 个真实 MySQL 测试连接本机 pet_system_test，覆盖当前数据、可见性、
keyset 查询及索引投影映射；每个用例事务回滚，不清理或覆盖现有记录。

未部署；交付为源码、相关测试与说明。
