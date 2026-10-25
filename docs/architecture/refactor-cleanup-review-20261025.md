# 整轮重构变化与旧代码清理评估（2026-10-25）

> 更新：以下为迁移前盘点。业务迁移收尾、旧父类依赖解除及本地验证见 [业务迁移收尾](business-migration-20261025.md)。共享模型、Mapper、DTO 和基础设施继续保留。
\n## 结论

核心业务实现基本已被新服务接管，但**不能直接清空 modules、common 或 AI 目录**。
本次没有删除任何旧代码，清理应作为单独任务逐批执行。

静态盘点结果：
- refactor 下有 220 个 Java 文件，包含业务、适配器、模型、枚举和基础设施。
- 13 个旧 ServiceImpl 有对应的新接口实现；未发现其他主代码直接使用这些旧类或默认 Bean 名。
- 其中 6 个未发现测试直接引用，可作为第一批候选；另外 7 个仍被旧新等价性或生命周期测试引用。
- 12 个新服务仍 extends 旧具体服务类，直接删除旧父类会编译失败。
- 还有 1 处新响应 DTO 继承旧 DTO；原实体、Mapper、接口、DTO、事件和公共配置被大量复用。

“实现被接管”与“旧文件已经没有依赖”是两件事。下面给出具体边界。

## 本地部署与验证

| 项目 | 当前结果 |
|---|---|
| React 前端 | http://127.0.0.1:5174/ |
| 本地后端 | http://127.0.0.1:8080/ |
| 数据库与依赖 | 通过 SSH 使用服务器 MySQL pet_system、Redis、ES、Qdrant、RocketMQ |
| 数据隔离 | 不是独立数据副本；在本地页面主动提交的业务操作仍会影响服务器数据 |
| 后台任务 | 本地禁用定时任务、XXL 注册及三个 MQ 消费者 |
| 启动副作用 | 本地禁用搜索重建、知识库向量同步、缓存预热 |
| 线上服务 | 本轮没有重启、替换线上 pms.jar 或发布前端 |
| 本地代码 | 含 9f53605 的 AI 重构，加 b5cbe60 本地配置和路由修复 |

新增 local-validation profile；默认生产启动行为保持原样。
本地实际启动的后台进程、日志及 PID 文件在 target/local-validation/；
当前修复版后端日志为 backend-fixed.out.log、backend-fixed.err.log。
React 使用 Vite 开发服务，已确认前端代理访问的是本地后端。

下次本机启动可运行：
powershell -ExecutionPolicy Bypass -File scripts/local/start.ps1

脚本默认打包后端，之后后台启动 SSH、Java、React；-SkipBuild 可复用已打包版本。
依赖本机 Java 17、Node 24、已安装的 PMS-react/node_modules 和已有 SSH 密钥。
脚本检测端口占用后会停止启动流程，不杀其他进程；不要在当前实例还运行时重复启动。
构建使用已有 Maven settings（存在时），凭据仍由被忽略的 application.yaml 提供，
没有把数据库/Redis/模型密钥写进脚本或新 profile。
启动脚本完成语法校验；当前进程使用等价参数启动并完成实际验证。

### 发现并修复的问题

完整加载两个 AI 控制器后，发现：
- 清会话缓存：DELETE /ai/memory/{sessionId}
- 删除长期记忆：DELETE /ai/memory/{id}

原模式相同，Spring 在实际调用时出现 Ambiguous handler methods。
本次为长期记忆限定 1～19 位数字 ID，为会话限定 20～64 位字母、数字、下划线或连字符；
已生成的 32 位会话 UUID、兼容的长会话 ID 和长期记忆数字 URL 不变，
纯数字的 32 位会话 ID 也不会误入长期记忆路由。
新增两个控制器同时加载的回归测试，不再只测单个控制器。

### 验证结果

- 本次 12 个相关配置、调度与 AI 路由测试全部通过。
- 本地 10 组实际连通检查全部通过：会话创建/列表、真实引擎的免模型转人工分支、
  持久化回复 ID、反馈、越权拦截、清缓存、转人工去重、删除关闭转接、真实 WebSocket、
  8 个核心读取接口和测试数据清理。
- 免模型分支仍执行真实消息持久化，没有发送外部模型请求或产生模型费用。
- 临时账号、AI 会话、消息和转接记录已按归属清理；探测令牌已退出。
- 修复后运行实例没有新增 ERROR，也没有启动线上 MQ 消费、索引重建或定时任务。

这不是全部页面人工验收、真实模型质量评估或长时间压力测试。

## 后端业务变化

| 范围 | 新入口/职责 | 主要变化 |
|---|---|---|
| 领养/救助 | PmsPetPostServiceImpl、转换器与 PmsPetPost 模型 | 原路径接管，明确字段映射、审核可见性、编辑重审与终态规则；收藏仍由宠物服务承接 |
| 活动内容 | ActivityCommands / ActivityQueries / ActivityAssembler | 内容写入、列表详情、作者装配分离；已结束内容禁止修改 |
| 活动报名/签到 | ActivityParticipationCommands | 报名记录、名额和签到在事务内协调，处理取消/并发竞争，延迟业务持久化 |
| 评论 | PmsCommentServiceImpl、目标能力及事件 | 修复重复加计数，批量装配作者和评论树，通知在提交后处理；删除入口仍未实现，见后文 |
| 点赞 | PmsLikeServiceImpl / LikeTarget | 按目标注册策略，统一点赞切换与计数，不再各模块复制类型分支 |
| 关注 | PmsFollowServiceImpl、FollowWriteMapper / InboxWriter | 修复关系方向，双方计数及历史 Feed 回填同事务，列表状态按访问者装配 |
| Feed | PmsFeedServiceImpl / FeedAssembler | 数据批量装配、可见性和互动状态校验、未读及清理流程收敛 |
| 私信 | PmsPrivateMessageServiceImpl、会话视图与写入 Mapper | 参与者校验，双方删除视图、预览及未读计数，发送/读取/清空事务 |
| 消息通知 | PmsMessageService / MessageInbox、通知策略 | 消息模板与存储/推送分开，检查消息归属，保留 MQ 和 WebSocket 链路 |
| 公告 | PmsNoticeServiceImpl、发布时间与缓存监听 | 内容生命周期、定时发布、已读和缓存一致性；旧 Redis 发布循环停用 |
| 审核 | AuditQueryService / AuditCommandService、目标策略 | 分离查询与写入，待审记录和内容同事务推进，编辑新轮次保留历史，阻止重复处理 |
| 举报 | PmsReportServiceImpl、举报目标策略 | 举报状态与目标操作协调，处理并发和提交后缓存/搜索更新 |
| 用户与后台账号 | UserAuthentication / Profiles / Administration / AvatarCommands | 注册、登录、资料、头像、账号治理分离；当前角色/禁用与密码版本校验，退出只撤销当前会话 |
| 日常、话题、推荐 | DailyCommands / Queries / Assembler、召回策略、画像服务 | 服务端字段白名单、话题关联事务、审核可见性、画像和行为一致性、四路召回 |
| 数据统计 | StatisticsQueries / Collector / Contributors / DayWriter | 统一日期窗口、计数口径、空日处理；按日独立事务重建、补中间缺口、失败保留旧快照 |
| 搜索查询 | SearchBackend / SearchSource / Projection / Enrichment | ES 召回排序，数据库校验当前可见性并返回最新资料、计数及互动；安全高亮 |
| 搜索写入/重建 | SearchIndexCoordinator / Source / Store / Maintenance | 提交后按 ID 对账、同文档串行；新物理索引构建成功后切别名，保留旧索引和失败反馈 |
| 调度 | PmsJobActions / ScheduledTaskWorker / XxlJobHandlers | 本地与 XXL 模式互斥，统一任务入口、看门狗锁及业务任务数据库状态 |
| AI 会话与消息 | AiConversationGateway / Sessions / Transfers / MessageWriter | 所有权、转人工状态、消息/计数事务、真实回复 ID、REST/WS 统一入口 |
| 公共能力 | core / extension | 枚举契约、目标方言、防护、分页、字段填充、策略注册和提交后副作用等复用点 |

设计模式主要用在真实变化点：目标/召回/通知/统计的策略与注册表，
旧接口适配器和门面，提交后事件，以及职责分离。
没有把所有业务塞进统一 BaseService，也没有拆成微服务或宣称完成全量 DDD 改造。

## 数据模型实际做了什么

1. 建立 BaseEntity / AuditEntity 等公共模型与字段填充能力，
   宠物新模型用明确的 petBreed / petAgeText 映射老 pet_type / pet_age。
2. 状态按业务定义枚举，目标类型通过 TargetTypeCodec 兼容历史方言，
   没有把不同含义的 status 数字强行统一。
3. 增加面向查询的投影、批量装配和领域事件，不把所有实体直接作为外部写入对象。
4. 大多数实体、DTO、Mapper 和物理表继续复用，没有完成全库模型统一或切表。
5. 旧 JSON 字段、状态历史、无外键、DDL 漂移等并未因服务重写而全部消失。
   db/core-v2.sql 等预备结构不代表线上已经切换；不能仅改表名就认为跨模块迁移完成。
6. 新读入口常回查数据库，旧个性化缓存被绕开或失效；这不等于任意直接 SQL 修改都会自动发事件。

## 前端变化与旧工程

主前端现为 PMS-react，本次启动也使用它；保留 PMS-vue 和 new-pms。
已有 React 提交覆盖原型风格、互动区域、AI 与管理工作台、消息/私信、
私信弹窗、页面连续切换、图片延后加载、按钮加载圈和爪印 favicon。
这里记录当前工程及历史提交范围，不把本次后端验证描述为全部 UI 重新验收。

旧工程不能顺手一起删除：
- deploy.sh 仍引用 PMS-vue 构建和 dist。
- 前端 Jenkinsfile 虽存放在 PMS-vue 目录内，其实际构建/部署已指向 PMS-react。
- 需先整理 CI/CD、构建产物和部署路径，再决定前端旧工程归档方式。

## 旧代码清理清单

### A. 6 个可优先评估的旧实现

这 6 个实现已有新接口实现，静态扫描未发现其他主代码的旧类名/默认 Bean 名引用，
也未发现测试直接引用。它们是候选，并不是本次已经删除或证明删除后必然安全：

| 旧实现 | 新实现 |
|---|---|
| [AuditServiceImpl](../../src/main/java/com/hongjie/pms/modules/audit/service/Impl/AuditServiceImpl.java) | [PmsAuditServiceImpl](../../src/main/java/com/hongjie/pms/refactor/audit/PmsAuditServiceImpl.java) |
| [FeedServiceImpl](../../src/main/java/com/hongjie/pms/modules/feed/service/Impl/FeedServiceImpl.java) | [PmsFeedServiceImpl](../../src/main/java/com/hongjie/pms/refactor/feed/PmsFeedServiceImpl.java) |
| [FollowServiceImpl](../../src/main/java/com/hongjie/pms/modules/following/service/Impl/FollowServiceImpl.java) | [PmsFollowServiceImpl](../../src/main/java/com/hongjie/pms/refactor/following/PmsFollowServiceImpl.java) |
| [LikeServiceImpl](../../src/main/java/com/hongjie/pms/modules/like/service/Impl/LikeServiceImpl.java) | [PmsLikeServiceImpl](../../src/main/java/com/hongjie/pms/refactor/like/PmsLikeServiceImpl.java) |
| [PrivateMessageServiceImpl](../../src/main/java/com/hongjie/pms/modules/privateMessage/service/Impl/PrivateMessageServiceImpl.java) | [PmsPrivateMessageServiceImpl](../../src/main/java/com/hongjie/pms/refactor/privateMessage/PmsPrivateMessageServiceImpl.java) |
| [ReportServiceImpl](../../src/main/java/com/hongjie/pms/modules/report/service/Impl/ReportServiceImpl.java) | [PmsReportServiceImpl](../../src/main/java/com/hongjie/pms/refactor/report/PmsReportServiceImpl.java) |

删除前仍要检查 Spring 装配、反射/配置依赖、接口契约及相关业务回归，
并完成一次完整启动。不要凭“主代码零直接引用”跳过这些检查。

### B. 另外 7 个实现：先迁移旧实现相关测试

这些旧实现同样已被新实现接管，但测试仍直接用到旧类。
删除会导致相关测试编译失败或丢失等价性/生命周期基线：

| 旧实现 | 新实现 | 测试文件引用 |
|---|---|---|
| [ActivityServiceImpl](../../src/main/java/com/hongjie/pms/modules/activity/service/Impl/ActivityServiceImpl.java) | [PmsActivityServiceImpl](../../src/main/java/com/hongjie/pms/refactor/activity/PmsActivityServiceImpl.java) | 2 |
| [AdminServiceImpl](../../src/main/java/com/hongjie/pms/modules/admin/service/Impl/AdminServiceImpl.java) | [PmsAdminServiceImpl](../../src/main/java/com/hongjie/pms/refactor/admin/PmsAdminServiceImpl.java) | 1 |
| [CommentServiceImpl](../../src/main/java/com/hongjie/pms/modules/comment/service/Impl/CommentServiceImpl.java) | [PmsCommentServiceImpl](../../src/main/java/com/hongjie/pms/refactor/comment/service/PmsCommentServiceImpl.java) | 3 |
| [NoticeServiceImpl](../../src/main/java/com/hongjie/pms/modules/notice/service/Impl/NoticeServiceImpl.java) | [PmsNoticeServiceImpl](../../src/main/java/com/hongjie/pms/refactor/notice/service/PmsNoticeServiceImpl.java) | 2 |
| [PetPostServiceImpl](../../src/main/java/com/hongjie/pms/modules/petpost/service/Impl/PetPostServiceImpl.java) | [PmsPetPostServiceImpl](../../src/main/java/com/hongjie/pms/refactor/petpost/service/PmsPetPostServiceImpl.java) | 3 |
| [AvatarServiceImpl](../../src/main/java/com/hongjie/pms/modules/user/service/Impl/AvatarServiceImpl.java) | [PmsAvatarServiceImpl](../../src/main/java/com/hongjie/pms/refactor/user/PmsAvatarServiceImpl.java) | 2 |
| [UserServiceImpl](../../src/main/java/com/hongjie/pms/modules/user/service/Impl/UserServiceImpl.java) | [PmsUserServiceImpl](../../src/main/java/com/hongjie/pms/refactor/user/PmsUserServiceImpl.java) | 2 |

测试数量是引用该旧类的测试文件数，不是测试用例数。
相关测试应保留约定与期望结果，迁移为新服务契约测试或归档旧新对比基线，
不建议直接删测试来消除报错。

### C. 12 个旧具体服务父类：必须先解耦

以下新类仍 extends 旧具体服务类，直接删旧父类会使主代码编译失败：

| 新适配器 | 必须保留至解耦的旧父类 |
|---|---|
| [PmsChatMemoryService](../../src/main/java/com/hongjie/pms/refactor/ai/PmsChatMemoryService.java) | [ChatMemoryService](../../src/main/java/com/hongjie/pms/AI/modules/service/ChatMemoryService.java) |
| [PmsChatSessionService](../../src/main/java/com/hongjie/pms/refactor/ai/PmsChatSessionService.java) | [ChatSessionService](../../src/main/java/com/hongjie/pms/AI/modules/service/ChatSessionService.java) |
| [PmsHumanTransferService](../../src/main/java/com/hongjie/pms/refactor/ai/PmsHumanTransferService.java) | [HumanTransferService](../../src/main/java/com/hongjie/pms/AI/modules/service/HumanTransferService.java) |
| [PmsDailyPostService](../../src/main/java/com/hongjie/pms/refactor/daily/PmsDailyPostService.java) | [DailyPostService](../../src/main/java/com/hongjie/pms/modules/daily/service/DailyPostService.java) |
| [PmsDailyRecommendService](../../src/main/java/com/hongjie/pms/refactor/daily/PmsDailyRecommendService.java) | [DailyRecommendService](../../src/main/java/com/hongjie/pms/modules/daily/service/DailyRecommendService.java) |
| [PmsDailyUserInterestService](../../src/main/java/com/hongjie/pms/refactor/daily/PmsDailyUserInterestService.java) | [DailyUserInterestService](../../src/main/java/com/hongjie/pms/modules/daily/service/DailyUserInterestService.java) |
| [PmsTopicService](../../src/main/java/com/hongjie/pms/refactor/daily/PmsTopicService.java) | [TopicService](../../src/main/java/com/hongjie/pms/modules/daily/service/TopicService.java) |
| [PmsMessageService](../../src/main/java/com/hongjie/pms/refactor/message/PmsMessageService.java) | [MessageService](../../src/main/java/com/hongjie/pms/modules/message/service/MessageService.java) |
| [PmsUnifiedSearchService](../../src/main/java/com/hongjie/pms/refactor/search/PmsUnifiedSearchService.java) | [UnifiedSearchService](../../src/main/java/com/hongjie/pms/modules/search/service/UnifiedSearchService.java) |
| [PmsFullDataSyncService](../../src/main/java/com/hongjie/pms/refactor/search/index/PmsFullDataSyncService.java) | [FullDataSyncService](../../src/main/java/com/hongjie/pms/modules/search/service/FullDataSyncService.java) |
| [PmsSearchDataSyncService](../../src/main/java/com/hongjie/pms/refactor/search/index/PmsSearchDataSyncService.java) | [SearchDataSyncService](../../src/main/java/com/hongjie/pms/modules/search/service/SearchDataSyncService.java) |
| [PmsStatisticsService](../../src/main/java/com/hongjie/pms/refactor/statistics/PmsStatisticsService.java) | [StatisticsService](../../src/main/java/com/hongjie/pms/modules/statistics/service/StatisticsService.java) |

应先抽取服务契约或将旧同名类型改为契约，去掉新适配器的 super 构造依赖，
并核对继承的方法、事务/异步等注解语义，再删除旧业务实现。
仅去掉 @Service 或 @Primary 不能解除 Java 继承依赖。

另外 ActivityDetailResponse 仍 extends ActivityDetailRespDto。
这个旧 DTO 本来就被要求保留，不应为了“清旧实现”一并删除。

### D. 当前仍是有效依赖，不能按“旧目录”删除

- 原 Service 接口、Controller、DTO、实体、Mapper、搜索事件及 XML/SQL 映射。
- common 下的认证、缓存、MQ、WebSocket、配置、异常和第三方工具。
- 生产仍在用的 CacheUpdateConsumer、MessageMqConsumer、DelayTaskConsumer；
  本地关闭消费不代表线上已不用它们。
- AIAgentEngine、ToolRegistry、各 AI 工具、RAG、UserMemoryService、AiMemoryExtractor；
  它们多数仍是实际实现，不是已废弃的旧备份。
- PetCategoryClassifier 仍被新宠物服务调用，分类字典与分类器硬编码尚未完全统一。
- 任何被 Redis 类型元数据、MQ/事件 payload、配置、脚本引用的类型与路径，
  都需要单独核对兼容性和遗留数据。

## 尚未完成或需要单独补齐

| 项目 | 当前边界 |
|---|---|
| AI 上下文重置、长期记忆、引擎/RAG编排 | 还没有完整重构；清 Redis 缓存不等于让模型忘记数据库历史 |
| 评论删除 | CommentController.deleteComment 仍注释掉服务调用，成功响应不会执行删除；不是本次重写已覆盖的功能 |
| 搜索建议 | suggest 仍返回空列表 |
| 持久化事件与重试 | TableOutboxStore 基架默认关闭；未见完整 markSent/markFailed 调用闭环和自动重投器，不是已上线的可靠消息系统 |
| 搜索增量重试 | 队列失败有计数/日志，但没有持久化自动补偿队列 |
| 历史统计 | 只有最后活跃时间等当前态，无法准确重建所有历史 DAU 和已物理删除行为 |
| 模型/DDL统一 | 没有完成全库审计字段、逻辑删除、版本字段、迁移版本和字典规则统一 |
| 长时间稳定性 | 有相关自动化、真实服务启动与核心链路检查，尚不能替代长期压测与持续运行观察 |

上表是当前清理决策的重要输入，不表示必须补完所有新功能才能删除某个独立旧实现。
但不能把“当前接口被新服务接管”表述成“所有功能和可靠性改造已经完结”。

## 建议的清理顺序

1. 保存可恢复的代码版本和已验证部署包，本地确认最新 AI/路由修复，必要时先安排单独上线验证。
2. 先处理 A 组 6 个旧实现，逐模块删除、编译、相关测试和完整启动验证。
3. 迁移 B 组基线测试后，再清另外 7 个旧实现。
4. 将 C 组 12 处继承改为明确契约与委托，核对继承注解，再去掉旧实现。
5. 最后整理 Pms 前缀、refactor 包归位和公共模型；保留实际复用的 DTO/实体/Mapper。
6. 另开前端/脚本/DDL归档工作，先消除部署引用再删除旧工程和旧脚本。

本轮没有执行上述删除步骤，等待你决定清理范围。

## 早期文档的修正

overview.md 与 ADR 0006 中“删 refactor 目录即可回滚”的说法属于首轮方案，当前已不成立：
共享 Controller、认证、调度等适配层已经引用新包，反向依赖客观存在。
回滚应恢复经过验证的代码和部署版本，并核对配置及搜索别名，
不能再靠删除单个目录操作。
两份旧文档已加过时提示，当前以本报告和各模块后续记录为准。

## 证据与验证记录

- 目录与类型关系由当前源代码静态扫描获得，未把注释中的类名当运行引用；
  同时检查旧类名和默认 Bean 名，但不是完整的运行时/反射可达性证明。
- 详细模块记录见同目录各 refactor 文档。
- 线上核心发布基线见 [核心发布记录](core-release-20261025.md)：216 个相关测试、
  29 项线上检查、5 个 XXL 回调通过，部署代码为 b97f2a9。
- 最新 AI 重构 9f53605 的 59 个相关测试通过；本次新增 12 个相关配置/路由检查，
  本地实际连通检查见 target/local-validation/smoke-result.json。
- 历史测试执行数有重复，不能把各次数字直接相加当作唯一测试总数或覆盖率。
