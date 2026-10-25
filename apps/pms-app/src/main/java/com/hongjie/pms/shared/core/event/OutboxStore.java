package com.hongjie.pms.shared.core.event;

/**
 * 事务性发件箱存储端口。
 *
 * <p>定义成端口而非直接依赖 Mapper，是为了让 {@code refactor.core} 不依赖任何业务模块 ——
 * 具体实现（基于 {@code pms_domain_event} 表）放在 Phase 2 的基础设施层。
 *
 * <p>语义要求：{@link #append} 必须与触发它的业务写操作<b>在同一事务</b>内完成，
 * 这样「业务数据落库」与「事件待投递」要么都成功、要么都回滚，
 * 避免出现「业务回滚了但消息已经发出去」或「业务提交了但事件丢了」两类不一致。
 */
public interface OutboxStore {

    /** 事务内追加一条待投递事件 */
    void append(DomainEvent event);

    /** 投递成功标记 */
    void markSent(String eventId);

    /** 投递失败标记，交由兜底重投任务按退避策略重试 */
    void markFailed(String eventId, String reason);
}
