package com.hongjie.pms.refactor.core.event;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 领域事件基类。
 *
 * <p>仅承载「事件身份」这一横切信息，具体载荷由子类以字段形式定义
 * （子类本身就是 payload，由 outbox 序列化落库）。
 *
 * <p>{@link #getEventId()} 是 outbox 表上的唯一键，也是消费端幂等的依据 ——
 * outbox 是「至少一次」投递，重复投递必须能被幂等消费处理。
 */
public abstract class DomainEvent implements Serializable {

    private final String eventId = UUID.randomUUID().toString();

    private final LocalDateTime occurredAt = LocalDateTime.now();

    /** 事件类型标识，用于 outbox 检索与消费端路由，如 {@code CommentCreated} */
    public abstract String eventType();

    /** 聚合根类型，如 {@code Comment} */
    public abstract String aggregateType();

    /** 聚合根主键，用于按业务对象追溯事件链 */
    public abstract Long aggregateId();

    public String getEventId() {
        return eventId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
