# 0006. 怎么保证重构可回滚

状态：已采纳（2026-10）

## 背景

这是一套**正在运行的系统**（有线上服务器、有生产事故记录、前端 PMS-vue 依赖它），
而且**全项目只有 1 个 13 行的 `contextLoads` 测试**。在这种前提下做架构级重构，
最大的风险不是"写不出来"，而是**改坏了却无法验证、也无法快速退回**。

## 可选方案

### 方案 A：新增 `/v2` 路径并行
新老接口并存，前端逐步迁移。

**否决**（中途修正）。最初选了这条路，做出 petpost/comment 的 `/v2` 控制器后
发现两个问题：
1. 两套接口并存意味着**双份维护面**，而老接口并没有因此减少；
2. 更要命的是前端要改两遍，与"接口不变"的目标相悖。

### 方案 B：原地改造老 Controller
直接改老控制器的方法体去调新实现。

**部分采纳**。用户的意见是"允许改 controller，但请求路径不变"。
但实测发现 petpost 与 comment 两个模块的 `Service` 接口**都只被各自的 Controller
按接口类型注入**，没有任何地方按具体实现类注入 —— 也就是说
**标一个 `@Primary` 就能接管，Controller 一行都不用改**。
既然允许改不等于必须改，最终选择了改动更小的做法。

### 方案 C：单根包 + `@Primary` 接管 + 删目录回滚（采纳）

```
com.hongjie.pms
├── AI/  common/  modules/     ← 老代码，未动
└── refactor/                   ← 全部重构代码
    ├── core/       BaseEntity, AuditEntity, 枚举, 分页, 注册表, 事件, Guard, 权限注解
    ├── extension/  目标处理器策略族, 防腐层, 通知策略族, 延迟/统计注册表
    ├── petpost/    PmsPetPost, Mapper, 转换器, TargetHandler, 实现类
    ├── comment/    实现类, 树转换器, 事件, 监听器
    └── message/    通知适配器
```

## 决定

1. 所有重构代码放在**一个根包** `com.hongjie.pms.refactor` 下，
   不散落在 `common/*` 与 `modules/*/v2/`；
2. 新实现**实现原有的 Service 接口**、返回原有的 DTO，标 `@Primary` 接管；
3. 老代码除了两处独立的缺陷修复外**一行未改**。

## 后果

### 回滚只需一步
删除 `refactor/` 目录。届时：

- `PetPostService` / `CommentService` 各自只剩老实现一个 bean，
  容器自动回退，**不需要改任何配置**，也不会有 bean 歧义；
- 依赖 `refactor` 的只有它自己（老包没有反向引用），所以老代码照常编译；
- 新增的 SQL 表本就没执行，无需清理。

### 收益
- 老代码零污染：这是"删目录即回滚"能成立的**前提**，也是当初否决方案 A
  中"给老实体加 `implements Identifiable`"的原因（那会让老实体反向依赖重构层）；
- `@Primary` 接管而非替换，意味着切换与回滚都只是**容器选择哪个 bean** 的问题，
  不涉及代码结构变化。

### 代价
- 同名类在 `modules.petpost.service.Impl.PetPostServiceImpl` 与
  `refactor.petpost.service.PmsPetPostServiceImpl` 同时存在，
  读代码时要看 import 才知道指哪个 —— 用 `Pms` 前缀在命名上做了区分；
- 重构层需要引用老层的实体、Mapper、服务（如 `AuditService`、`FeedService`、
  `OssUtils`），这是**逐模块迁移**的必然：只有该模块的内部实现重构了，
  依赖的其它模块仍是老的。等迁移完成，这些依赖会自然消失；
- `refactor/` 这个名字是过渡性的。全部迁移完成后它应该被"提升"为正式包结构
  （例如替换掉 `modules/`），届时 `refactor` 这个前缀就该去掉。

### 需要单独回滚的四处

老文件上有四处**有意的改动**，它们不随删除 `refactor/` 一起消失：

1. `common/handler/MyMetaObjectHandler.java` —— 加了一行 `@ConditionalOnProperty`
   （`matchIfMissing = true`，默认行为零变化），用于把字段填充切换成注解驱动版本；
2. `modules/comment/service/Impl/CommentServiceImpl.java` —— 两处真实缺陷修复
   （评论数被加两次、回复通知链接被三元运算符截断）；
3. `common/punishment/scheduler/DelayTaskScheduler.java` —— 派发改为
   「先查执行器、查不到再走老的 if-else」，并给未知任务类型补了一条告警日志。
   老的 6 个分支原样保留，因此行为不变。
4. `modules/notice/service/Impl/NoticeServiceImpl.java` —— 将旧 Redis 消费线程的
   `@PostConstruct` 改成 `SmartLifecycle`。上下文完成装配后，仅当按 `NoticeService`
   选中的仍是旧实现时启动；新实现接管时不访问 Redis、不创建线程。
   关闭时中断并等待线程退出，重复启动不创建第二个消费者。

四处都**不引用**重构层的任何代码，所以"删目录后老代码仍能编译"依然成立。
如果需要回到与改造前**逐字节一致**的状态，单独 revert 这四个文件即可
（第 3 处还要连带删掉下面那个 SPI 接口）。

### 一个刻意放在老包里的新接口

`common/punishment/scheduler/DelayTaskExecutor.java` 是本次重构新增的接口，
却放在**老包**里 —— 这看起来别扭，但它是保住回滚底线的关键。

`DelayTaskScheduler` 是 `delay_task` 表唯一的派发者，而且它会把**不认识的任务类型
也标记为已执行**（静默吞掉）。所以新的任务类型（公告定时发布）必须经由它派发。
如果让调度器去引用重构层的注册表，就形成了「老代码反向依赖新代码」——
删掉 `refactor/` 后老代码会编译不过，"删目录即回滚"当场失效。

把接口放在调度器所在的包里、**实现类放在重构层**，依赖方向就仍然是
「新代码引用老代码」（和 `PetPostService` 完全一致）。
删除 `refactor/` 后这个接口没有实现类，调度器照常编译、老任务类型照常执行。

同理，调度器没有引入重构层的 `StrategyRegistry`，而是自己用五行 `Map` 建索引 ——
复用现成的抽象很诱人，但**边界比复用重要**。

## 相关
- [0007 换表时机](0007-table-cutover-strategy.md)

## 2026-10-19 生命周期验证补充

`@Primary` 只决定注入选择，不会跳过旧 Bean 的生命周期。此前“旧消费者已停用”的
表述不成立；实际装配测试发现旧后台线程仍执行。此次修复保持旧代码不引用重构包，
通过当前接口的最终候选类型（兼容 Spring AOP 代理）决定是否启动旧消费者。

`LegacyNoticeLifecycleTest` 验证新实现接管时 Redis 零交互，以及只有旧实现时启动一次、
容器关闭后线程退出。`PrimaryTakeoverTest` 同时断言实际新公告实现被 Controller 注入，
且旧消费者未运行。

此处解决进程生命周期，不迁移历史 Redis 延迟队列中的任务。正式切换前仍需确认
`notice:delay:queue` 及对应 Redisson 延迟队列是否有待执行记录，按业务状态核实并迁入
`delay_task`；不得直接清空。回退到旧实现也不会自动处理新的 `NOTICE_PUBLISH` 数据库任务，
需先核对待执行任务，再选择回退窗口。
