# 0005. 事务内发消息怎么办

状态：已采纳（2026-10）

## 背景

老代码在两处把**外部副作用放在了数据库事务内部**：

1. `NoticeServiceImpl` 的 create/update/delete/publish/unpublish 五个方法标了
   `@Transactional`，方法体内却直接调 `cacheUpdateProducer.sendEvict*()`
   （发 RocketMQ，行 83/170/192/220）以及 Redisson 延迟队列 `offer()`（行 100-103）。
   事务一旦回滚，缓存失效消息已经发出去了。
2. `CommentServiceImpl.createComment` 同样在事务内调
   `messageService.sendCommentNotification()`，后者立刻发 MQ。

另外 `modules/search/listener/SearchDataSyncListener` 用的是 `@EventListener` + `@Async`，
而发布方在事务内 `publishEvent` —— 监听器在发布瞬间就执行，
**事务随后回滚的话，ES 里会残留从未提交的数据**。

这三处的共同问题是：事务的原子性只覆盖数据库，覆盖不到 MQ、Redis、ES。

## 可选方案

### 方案 A：把副作用挪到方法最后
简单，但只要事务在提交时才失败（比如约束冲突、连接断开），副作用依然会先于回滚发生。
没有真正解决问题。

### 方案 B：只做 `@TransactionalEventListener(AFTER_COMMIT)`
事务提交后才执行副作用，解决了"回滚后误发"。

**残留问题**：提交成功与副作用执行之间进程崩溃，事件就永久丢了。

### 方案 C：事务性发件箱（Outbox）+ `AFTER_COMMIT`（采纳）

```
事务内：业务写 + INSERT 一条 outbox 记录        ← 同一事务，同生共死
提交后：@TransactionalEventListener(AFTER_COMMIT) 投递
兜底：  定时任务扫描未投递成功的记录重投
```

## 决定

- 事件基类 `DomainEvent` + 发布入口 `DomainEventPublisher`（`@Transactional(REQUIRED)`，
  事务内只写库，不发 MQ）；
- 投递走 `@TransactionalEventListener(phase = AFTER_COMMIT)`，异常被捕获记录 ——
  事务已提交，通知失败不应让一个已经成功的操作对外表现为失败；
- **发件箱存储默认关闭**（`pms.event.outbox.enabled`）。

## 后果

### 收益
- 事务回滚 ⇒ outbox 记录随事务消失 ⇒ 不会产生任何外部副作用；
- 至少一次投递：进程崩溃后兜底任务能重投，消费端按 `event_id` 幂等。
  项目已有 `common/idempotent` 框架可复用；
- 顺带修掉了 ES 同步的数据残留问题。

### 代价
- 多一张表（`pms_domain_event`）与一个定时任务；
- 「至少一次」意味着消费端**必须**幂等，否则会重复通知；
- 排查问题时多了一层间接：不是"调了方法就发了消息"，而是"写了 outbox 才可能发"。

### 为什么默认关闭
`TableOutboxStore` 依赖新增表 `pms_domain_event`。若默认启用，而该表还没执行
`db/core-v2.sql` 建出来，那么**任何一次事件发布都会因插入失败而连带回滚业务事务** ——
表现就是"发一条评论失败"。

所以这里要求显式开启：

```yaml
pms:
  event:
    outbox:
      enabled: true
```

未开启时 `DomainEventPublisher` 只打一条警告并做进程内投递，
`AFTER_COMMIT` 监听器照常工作，**通知功能不受影响**，只是失去了持久化保证。
这是"新代码必须能在现有 schema 上直接跑起来"这一约束的直接体现。

### 与老通知链路的关系
`NotificationSender` 是一个端口，适配器
`refactor.message.NotificationSenderAdapter` 把新的值对象翻译成老的 `UserMessage`，
复用既有的 `MessageService.sendAndPush` → MQ → 消费者 →
`WebSocketHandler` 这条链路。**没有重写发送管道** ——
老链路已经带了 MQ 重试、熔断降级、离线存储，是经过线上验证的，
重写一遍只会引入新的不一致。

## 相关
- [0004 策略注册表取代 switch](0004-strategy-registry-over-switch.md)
