# 重构后架构总览

配套文档：[ADR 索引](../adr/README.md)

## 一、包边界与回滚

全部重构代码在一个根包下，删除它即完成回滚。

```mermaid
graph TB
    subgraph legacy["老代码（未改动）"]
        C1["modules/petpost/controller<br/>PetPostController"]
        C2["modules/comment/controller<br/>CommentController"]
        L1["modules/petpost/service<br/>PetPostServiceImpl（不再被引用）"]
        L2["modules/comment/service<br/>CommentServiceImpl（不再被引用）"]
        OTHER["modules/audit · feed · search<br/>like · favorite · report · statistics"]
    end

    subgraph ref["refactor/（新增，可整体删除）"]
        direction TB
        subgraph core["core —— 与业务无关"]
            BE["BaseEntity / AuditEntity"]
            EN["CodableEnum + 领域枚举"]
            PG["PageQuery / PageResult / PageBridge"]
            SR["StrategyRegistry"]
            EV["DomainEvent / DomainEventPublisher"]
            GD["Guard · @RequireRole"]
        end
        subgraph ext["extension —— 业务无关的扩展点"]
            TH["TargetHandler 策略族<br/>Commentable·Likeable·Favorable·Reportable"]
            ACL["TargetTypeCodec 防腐层"]
            NT["NotificationHandler 策略族"]
        end
        subgraph mod["模块实现"]
            P1["petpost<br/>PmsPetPostServiceImpl @Primary"]
            P2["comment<br/>PmsCommentServiceImpl @Primary"]
            P3["message<br/>NotificationSenderAdapter"]
        end
    end

    C1 -->|"按接口注入，@Primary 选中新实现"| P1
    C2 -->|"按接口注入，@Primary 选中新实现"| P2
    P1 --> P3
    P2 --> P3
    P3 -->|"复用既有发送链路"| OTHER

    ref -.->|"单向依赖：新代码引用老代码"| legacy
    legacy -.->|"禁止反向依赖，回滚的前提"| ref

    style ref fill:#e8f5e9,stroke:#2e7d32
    style legacy fill:#f5f5f5,stroke:#9e9e9e
```

**关键约束**：箭头只能从 `refactor/` 指向老代码，反向依赖被禁止 ——
这正是"删掉 `refactor/` 后老代码仍能独立编译"能成立的原因。

## 二、回滚示意

```mermaid
graph LR
    A["当前状态<br/>两套实现并存<br/>@Primary 选中新实现"] -->|"删除 refactor/ 目录"| B["回滚后<br/>只剩老实现<br/>容器自动回退"]
    A -->|"同时还要恢复老行为时"| C["额外 revert 两个老文件<br/>MyMetaObjectHandler<br/>CommentServiceImpl"]

    style A fill:#e3f2fd
    style B fill:#e8f5e9
```

回滚**不需要改任何配置**：`@Primary` 标记的新实现一消失，
`PetPostService` / `CommentService` 各自只剩一个候选 bean，容器自动注入老实现。

## 三、评论创建的完整链路

这条链路串起了本次重构的大部分机制：策略定位、事务边界、事件、通知渲染、适配器。

```mermaid
sequenceDiagram
    autonumber
    participant U as 客户端
    participant C as CommentController<br/>（未改动）
    participant S as PmsCommentServiceImpl
    participant R as TargetHandlerRegistry
    participant H as Commentable 处理器
    participant DB as MySQL
    participant P as DomainEventPublisher
    participant L as CommentNotificationListener<br/>AFTER_COMMIT
    participant F as NotificationFacade
    participant A as NotificationSenderAdapter
    participant MQ as RocketMQ（既有链路）

    U->>C: POST /pet-system/comment/create
    Note over C: 路径与入参均未变化
    C->>S: createComment(request)

    S->>S: Guard 校验 + BizTargetType.resolve<br/>（兼容 pet / pet_post / adopt）
    S->>R: get(PET_POST, Commentable.class)
    R-->>S: PetPostTargetHandler

    rect rgb(255, 243, 224)
        Note over S,DB: 同一个数据库事务
        S->>DB: INSERT comment
        S->>H: incrementCommentCount(postId)
        H->>DB: UPDATE pet_post SET comment_count = comment_count + 1
        Note right of H: 只自增一次<br/>老实现这里加了两次
        S->>P: publish(CommentNotificationEvent)
        P->>DB: INSERT pms_domain_event（开启时才写）
    end

    P-->>L: 事务提交后触发
    L->>F: notify(COMMENT, CommentNotificationContext)
    F->>F: 按 MessageType 定位策略
    F->>F: CommentNotificationHandler.build()
    F->>A: send(NotificationMessage)
    A->>MQ: 翻译成老 UserMessage，走既有发送链路

    S-->>C: 返回
    C-->>U: 200 评论创建成功
```

**两处关键设计在图中可见**：

- 橙色区块内是**同一事务**：业务写与发件箱写入同生共死，不会出现
  "业务回滚了但消息已经发出去"；
- 通知在**事务提交后**才走 MQ（`AFTER_COMMIT` 监听器），
  这是相对老实现（事务内直接发 MQ）的核心修正。

## 四、多态互动的消歧

原本散落在 6 处以上的类型分支，现在收敛成一次注册表查找。

```mermaid
graph LR
    REQ["请求里的 targetType<br/>pet / pet_post / adopt / comment_reply ..."] --> ACL["TargetTypeCodec<br/>读宽松"]
    ACL -->|"统一为枚举"| BT["BizTargetType"]
    BT --> REG["TargetHandlerRegistry"]
    REG -->|PET_POST| H1["PetPostTargetHandler"]
    REG -->|PET_ACTIVITY| H2["ActivityTargetHandler"]
    REG -->|DAILY| H3["DailyPostTargetHandler"]

    H1 --> CAP{"能力校验"}
    H2 --> CAP
    H3 --> CAP
    CAP -->|"Commentable"| M1["incrementCommentCount"]
    CAP -->|"Likeable"| M2["increment / decrementLikeCount"]
    CAP -->|"不支持的能力"| ERR["立刻抛错<br/>而不是静默走错分支"]

    style ERR fill:#ffebee
```

三套历史字面量（`pet_post` / `pet` / `adopt`）在入口被归一，
新代码只面对枚举；写入时再由防腐层按目标表还原成该表认得的方言。

## 五、当前状态与未完成项

| 项 | 状态 |
|---|---|
| `core` 基础层 | 完成，有单测 |
| `extension` 策略族与防腐层 | 完成，有单测 |
| petpost 模块重写 | 完成（15 个方法），有转换器单测 |
| comment 模块重写 | 完成（2 个方法 + 评论树） |
| notice 模块重写 | **未开始** |
| 其余 20 个模块 | **未开始** |
| 物理表切换 | **未进行**（见 [ADR 0007](../adr/0007-table-cutover-strategy.md)） |
| 运行时验证 | **未进行** —— 本机无法启动应用（依赖 MySQL/Redis/Qdrant/RocketMQ） |

验证现状：113 个单元测试全部通过，覆盖枚举与方言解析、分页桥接、策略注册表、
字段填充、通知文案、DTO 与评论树映射。**集成行为、以及"`@Primary` 在真实容器里
确实选中了新实现"，没有实跑验证过。**
