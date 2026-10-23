# 0004. 类型分支用 switch 还是策略注册表

状态：已采纳（2026-10）

## 背景

「按目标类型做不同的事」在项目里出现了 8 处以上，每处写法都不同：

| 位置 | 形式 | 问题 |
|---|---|---|
| `MessageService` | 20 个 `sendXxxNotification` + **4 段** `switch(targetType)` | 同一类型分支抄了 4 遍 |
| `ReportServiceImpl:130,203,222` | **3 处** `switch` 各写一遍 `pet/activity/comment/user` | 加一种目标要改 3 个地方 |
| `LikeServiceImpl:56` | 1 处 `switch` | |
| `CommentServiceImpl:75-132` | 3 段 `if / else if` | 每段重复"自增 + 查作者 + 发通知" |
| `DelayTaskConsumer:40-44` | `switch(dto.getType())` | 与 `DelayTaskHandler` 的重复实现 |
| `comment.target_type` 等 | 三套互相冲突的字面量 | `pet_post` / `pet` / `adopt` |

新增一种可互动目标，需要同时改上述 6 处以上。

## 可选方案

### 方案 A：继续用 switch，但集中到一处
写一个 `TargetTypeDispatcher`，内部一个大 switch。

**否决**。只是把散落的问题变成一个大函数的问题：每次新增类型仍要改这个核心方法，
违反开闭原则；而且不同调用点需要的**能力不同**（评论要自增评论数、点赞要增减点赞数、
举报要取摘要），一个大 switch 无法表达这种差异。

### 方案 B：每种互动各建一套注册表
评论一套、点赞一套、收藏一套、举报一套。

**否决**。查找、报错、重复键检测的逻辑完全同构，抄四遍是浪费。

### 方案 C：泛型注册表 + 窄能力接口（采纳）

```java
public class StrategyRegistry<K, S> {
    public StrategyRegistry(String name, Collection<S> strategies,
                            Function<S, Collection<K>> keyExtractor) {
        // 构造期建表，重复键立即抛异常（启动即失败）
    }
    public <T extends S> T get(K key, Class<T> capability) { ... }
}
```

能力接口保持**窄**：`TargetHandler`（`supportTypes/ownerId/title/detailLink`）为基类，
`Commentable` / `Likeable` / `Favorable` / `Reportable` 各自只加自己那一两个方法。

## 决定

一套泛型 `StrategyRegistry`，被四个领域复用：
`TargetHandlerRegistry` / `NotificationHandlerRegistry` / `DelayTaskRegistry` /
`StatCounterRegistry`。

## 后果

### 收益
- 新增一种可互动目标 = 新增一个实现类加 `@Component`，**注册表与所有调用点一行不改**；
- 构造期检测重复键，冲突时启动即失败，而不是等线上某个分支才暴露
  "两个策略抢同一个 key"；
- `get(key, Commentable.class)` 会在目标不支持该能力时**立刻**抛出
  "不支持 Commentable 能力"，而不是把 `ClassCastException` 留到调用点，
  也不是静默走错分支；
- 把老代码里 `case "系统通知"` 这类永远走不到的死分支自然消除了 ——
  key 变成枚举后非法取值不可表达。

### 代价
- **共享的是注册机制，不是策略接口。** 审计的 `AuditTargetHandler` 有
  `updateAuditStatus` / `clearCache`，评论目标没有；硬合成一个"万能 TargetHandler"
  会迫使每个实现类写一堆抛 `UnsupportedOperationException` 的空方法。
  所以 `AuditTargetHandlerFactory` 保留，只是内部改为持有
  `StrategyRegistry<BizTargetType, AuditTargetHandler>`。
- 只服务两个实现的注册表（如 `DelayTaskRegistry`）看起来是过度设计。
  这里之所以仍然用，是因为它同时承担了"消除两份重复实现"的职责
  （`DelayTaskHandler` 与 `DelayTaskConsumer` 的延迟任务逻辑几乎逐行重复）。
  如果没有这个附带收益，两个分支的场景不值得上注册表。

### 一个实现上的坑
`NotificationHandlerRegistry` 最初声明了 `<C extends NotificationContext> get(MessageType)`，
而父类是 `StrategyRegistry<MessageType, NotificationHandler<?>>`，其 `get(K)` 在
`K = MessageType` 时与前者**擦除后签名相同** —— 既不是重载也不是覆盖，
编译直接报"对 get 的引用不明确"。改名 `handlerFor` 解决。
这类"泛型 + 继承"的擦除冲突只有在真正编译时才会暴露。

## 相关
- [0002 枚举按限界上下文分](0002-enum-per-context.md)
- [0005 领域事件与发件箱](0005-domain-event-outbox.md)
