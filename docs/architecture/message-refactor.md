# 站内通知重构

## 范围与兼容入口

新增 refactor.message.PmsMessageService 接管现有 MessageService 类型的注入。原 MessageController、MessageService、实体、MQ DTO 和表保留，/pet-system/message 下 list、unread-count、read/{id}、read-all 的路径、参数及响应结构不变。

由于旧业务模块直接依赖具体 MessageService 类，兼容类暂时继承它以保持类型兼容，但覆盖其全部 19 个公开方法；测试对完整公开方法集合进行保护，避免新增旧入口后意外落回旧实现。父类仅提供类型与构造契约，新入口不执行父类业务方法。后续可逐步将调用方迁移到窄接口，取消该继承。

MessageInbox 独立负责用户收件箱，MessageDelivery 独立负责发送生命周期，PendingNotification 是不可变载荷快照，PmsMessageService 只做旧调用适配及通知模板装配。点赞、评论复用现有 LikeNotificationHandler、CommentNotificationHandler 和目标类型编码器，不另起策略注册表，也不注入 NotificationFacade 形成 sender -> service -> facade -> sender 的循环依赖。

其他通知通过统一 emit 骨架装配。保持通知类型、标题、正文、业务 ID 与链接兼容，包括前端使用的 PUNISHMENT，不能擅自改为不同枚举中的 PUNISHMENT_START/PUNISHMENT_END。类型筛选不限制为现有枚举，保留历史类型兼容；空白类型不筛选。点赞/评论允许历史目标别名，未知目标沿用通用文案兜底，“系统通知”的旧特殊点赞分支也保留。

## 收件箱与原子已读

每个收件箱入口都验证当前登录者；接受 actor 参数的方法还要求与上下文一致，防止绕过 Controller 的跨用户查询。分页复用 PageQuery，默认 20、最大 100，以 create_time 和 id 降序保证同时间的确定顺序，返回 MyBatis 原有分页结构。

单条已读以 id + user_id + is_read=0 条件更新，同时写入阅读时间。未更新时仅查询以区分不存在、非本人和已经已读；不以整实体 updateById 覆盖并发变化。重复已读保留首次阅读时间。全部已读按当前用户、未读状态及可选类型一次更新，不触及其他用户和其他类型。消息缺失仍报 NOT_FOUND，非本人报 FORBIDDEN。

## 事务投递

发送将 UserMessage 即时转换为 PendingNotification，防止调用者在事务结束前修改原对象，导致延迟消息被篡改。批量发送先完成全部快照和模型校验，再开始排队，避免无活动事务时因批次后半段无效输入而部分发出。非空接收者、类型长度、非空正文、标题和链接上限遵循既有 user_message 列约束；TEXT 实际字节上限仍由数据库约束。

投递使用 @TransactionalEventListener(fallbackExecution=true)：活动事务中排队，提交后执行，回滚不执行；无活动同步事务时即时投递。这也支持由现有 Spring AFTER_COMMIT 监听器再触发的通知，不依赖已结束的同步事务二次注册。每次 MQ 投递及同步降级落库都通过 REQUIRES_NEW TransactionTemplate 隔离，使提交后的本地落库可以确实提交。

发送最终仍调用原 MessageMqProducer，保留 RocketMQ、消费熔断、本地降级与 WebSocket 链路。投递异常记录接收者/类型/业务 ID，不记录正文，不反向影响已提交业务。这里没有持久化 outbox、自动补偿或消息唯一 ID，不能保证宕机不丢或消费恰好一次；旧消费者的重复落库风险尚未改造。无事务直接发送失败同样只记录日志，不再把投递失败抛给业务调用方。

现有点赞、评论、关注等提交后通知入口继续工作；无需各模块再复制发送模板或事务判断。旧 NotificationSenderAdapter 继续使用同一 MessageService 类型，实际注入由 @Primary 解析至新实现。

## 测试与回退

专项测试包括 7 项单元/兼容性测试和 10 项 MySQL 场景。兼容测试比较 23 组旧文案与载荷（忽略发送时间），检查所有公开入口覆盖、不可变快照、匿名及伪造身份、外部消息已读保护与幂等行为。

MessageMySqlTest 通过 PMS_MESSAGE_MYSQL_TESTS 显式启用，只允许本机 3306 的 pet_system_test，检查 user/user_message 均为 InnoDB。使用随机标记用户及消息，清理仅限定本轮拥有的记录，并确认用户已删除；没有改表、共享种子或全库清理。

真实数据库测试验证分页与历史类型、越权拒绝、并发已读、类型筛选读全部、事务提交前不发送与快照保护、回滚抑制通知及已读恢复、现有提交后监听器触发、通知降级写入独立提交、发送失败不影响已提交业务、无事务无效批次不部分发送，以及原 Controller/NotificationFacade 接管且无循环依赖。MQ 传输使用 mock 并模拟其同步降级数据库插入，未连接真实 MQ 或 WebSocket，也未验证消费重试/幂等。

2026-10-23 验证结果：17 项专项测试全部通过（含本机 MySQL 10 项）；排除原有依赖外部服务的 PmsApplicationTests、关闭数据库测试后，整体回归 268 项中 220 项通过、48 项显式跳过，无失败。数据库专项已单独执行，不以整体回归中的跳过代替验证。完整启动、实际 MQ/WebSocket 联调和负载测试未在本轮执行。

回退须同时排除本批 PmsMessageService、MessageInbox、MessageInboxMapper、MessageDelivery 的扫描，恢复旧 MessageService 注入；既有 NotificationSenderAdapter 及策略注册表仍应保留。不能排除整个 refactor.message 包误移除既有 NotificationSenderAdapter，也不能只移除 @Primary 导致双实现歧义。未改表，无反向数据迁移。
