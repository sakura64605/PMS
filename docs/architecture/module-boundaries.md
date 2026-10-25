# 后端模块结构与微服务拆分边界

本次先整理为模块化单体：保留一个 Maven 工程、PetSystemApplication 启动类和现有 HTTP 路径。目录归属已经明确，但跨模块事务和数据访问仍需继续解耦，不能直接将目录打成多个服务启动。

## 目录

```text
src/main/java/com/hongjie/pms/
├── PetSystemApplication.java
├── bootstrap/                    # 当前单体的装配、Web/WS 配置与预热
├── platform/                     # Redis、MQ、鉴权上下文、幂等、限流、异常等技术设施
├── shared/                       # 通用分页、枚举、事件、策略及互动能力契约
└── modules/
    ├── identity/                 # user、admin：账户、认证、资料、头像、用户管理
    ├── content/                  # petpost、daily：领养/救助、分类、日常、话题、推荐
    ├── activity/                 # 活动发布、报名、签到与生命周期
    ├── community/                # comment、like、following、feed
    ├── communication/            # notification、privatemessage、notice
    ├── governance/               # audit、report：内容审核和举报
    ├── ai/                       # 会话、长期记忆、Agent、工具和知识库
    ├── search/                   # 搜索查询、索引同步与维护
    ├── analytics/                # 数据统计和快照
    └── scheduling/               # XXL-JOB 接入、任务记录和调度

src/main/resources/mapper/         # 按模块组织 MyBatis XML
src/main/resources/compatibility/  # 持久化 Java 类型别名，迁移兼容资料
src/test/java/.../modules/         # 对应业务模块测试
src/test/java/.../architecture/    # 包布局、依赖基线、集成装配约束
src/test/java/.../platform/        # 技术设施测试
src/test/java/.../shared/          # 通用层测试
database/                         # schema、legacy、manual、seeds；不会自动执行
docs/architecture/archive/        # 历史架构图
```

模块内按职责放置：`api/controller`、`api/websocket`、`api/service`、`api/dto`、`api/event` 是入口或对外契约；`application` 承担业务用例、编排、转换与策略；`domain` 存放已有领域规则；`infrastructure` 放持久化实体、Mapper、缓存、消息和配置。现有 MyBatis 实体属于持久化模型，没有仅因整理目录就将其宣称为独立领域聚合。原 `AI`、`common`、`refactor` 顶层包已合并到上述归属，不再并存两套业务目录。

## 模块与主要数据归属

| 模块 | 负责的数据/能力 | 拆分前需要处理 |
|---|---|---|
| identity | user、avatar_history、账户及管理 | 对外提供认证、用户简档和限制状态 API，其他模块不直接读写用户表 |
| content | pet_post、pet_category、daily_post、topic、daily_* | 审核、互动计数、Feed 改通过契约或事件协作 |
| activity | activity、activity_signup | 用户限制、报名处罚、审核与任务回调不能依赖跨库行锁 |
| community | comment、like_record、favorite_record、follow、user_inbox | 明确各表最终所有者，以目标能力 API 或事件更新内容计数；当前收藏表仍位于 petpost 持久化包 |
| communication | user_message、private_conversation、private_message、notice、notice_read_record | 统一通知投递契约、用户查询和客户端实时连接路由 |
| governance | audit_record、report_record | 通过被治理模块的 API/事件执行状态变更，停止跨模块 Mapper 写入 |
| ai | ai_chat_*、ai_user_memory、ai_human_transfer、ai_knowledge_base、Qdrant | 工具通过业务查询 API 取数据，避免直接访问业务实体与 Mapper |
| search | Elasticsearch 索引及维护状态 | 改由业务事件/查询契约构建投影，索引不是业务数据权威源 |
| analytics | daily_statistics、统计投影 | 拆分后不能依赖一个数据库中的多表统计，应通过事实事件或查询接口汇总 |
| scheduling | delay_task、XXL-JOB 执行入口 | 拆出业务任务处理端口，避免调度进程直接持有其他服务的数据库锁 |

## 已做的保护

- Java 包名与文件路径同步；主包扫描范围不变，测试包同样按模块归属迁移。
- MyBatis XML namespace 和资源位置同步更新，并验证自定义语句能绑定到 Mapper。
- Redis 历史类型通过 `compatibility/java-type-aliases.properties` 映射。读兼容旧/新类名，写继续使用历史类型 ID，支持已有实例与当前本地验证实例共用缓存；不通过清空 Redis 解决改包问题。别名只应用于类型元数据，不改普通业务字符串。
- `ModuleLayoutTest` 检查源码路径、公共层不反向依赖业务，以及新增跨模块内部依赖；`BusinessOwnershipTest` 继续检查旧实现不存在。
- 现存跨模块内部引用记录在 `module-internal-dependencies.json`。目前有 181 条 import/引用项，这是迁移债务清单，不能理解为已经隔离完成。
- 运行 `python scripts/architecture/check_module_dependencies.py` 查看关系并阻止新增内部引用。只有实际审查了必要性后才用 `--update-baseline` 更新基线；优先提供模块 API，不以扩大白名单代替解耦。

## 后续拆分顺序

1. 先替换跨模块实体、Mapper、内部 Service 调用，明确 API DTO 与事件；优先处理 AI 工具查询、通知投递、搜索投影及统计读取这些边界较清楚的链路。
2. 将本地事务限制在所属模块；需要跨模块提交的操作补齐可靠事件、幂等及补偿，再决定数据复制、读模型和独立数据库。
3. 最后按确定的边界拆 Maven 子模块和独立启动服务，调整网关、认证传播、WebSocket、XXL-JOB 和部署配置。当前 bootstrap 负责单体装配，届时由各服务的启动装配接替。

本次不变更数据库表、不新增微服务网络调用，也不部署线上服务。已完成受影响业务回归；结构约束、Redis 类型别名、MyBatis XML 和启动配置重点补跑 11 项通过，构建成功。本地原启动类启动成功，10 组真实 HTTP/WebSocket 冒烟通过，测试账号已清理。
