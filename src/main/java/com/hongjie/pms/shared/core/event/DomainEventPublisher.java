package com.hongjie.pms.shared.core.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 领域事件发布入口 —— 事务内落 outbox，事务提交后才真正投递。
 *
 * <p><b>要解决的问题</b>：老代码在 {@code @Transactional} 方法里直接发 RocketMQ
 * （如 {@code NoticeServiceImpl} 的 create/update/delete/publish/unpublish，4 处），
 * 一旦事务随后回滚，消息已经发出去了；而 {@code SearchDataSyncListener} 用的是
 * {@code @EventListener} + {@code @Async}，监听器在发布瞬间就执行 —— 事务回滚后
 * ES 里会残留从未提交的数据。
 *
 * <p><b>本类的做法</b>：
 * <ol>
 *   <li>在业务事务内只做一次 {@code INSERT}（写 outbox），不发任何 MQ；</li>
 *   <li>同时发布 Spring 事件，由 {@code @TransactionalEventListener(AFTER_COMMIT)}
 *       的监听器在提交后才执行真正的投递。</li>
 * </ol>
 * 这样事务回滚 ⇒ outbox 记录随事务消失 ⇒ 不会有任何外部副作用。
 *
 * <p><b>降级</b>：若 {@link OutboxStore} 未装配（Phase 1 阶段尚未建表），
 * 只做进程内投递并打印告警，不影响启动。Phase 2 装配真实存储后自动生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final ApplicationEventPublisher springPublisher;

    private final ObjectProvider<OutboxStore> outboxStoreProvider;

    /**
     * 发布事件。
     *
     * <p>标记 {@code REQUIRED} 是为了保证一定有活动事务：若调用方已在事务中则加入，
     * 若没有则新建一个 —— 否则 {@code AFTER_COMMIT} 监听器永远不会被触发。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void publish(DomainEvent event) {
        OutboxStore outbox = outboxStoreProvider.getIfAvailable();
        if (outbox != null) {
            outbox.append(event);
        } else {
            log.warn("OutboxStore 未装配，事件 {} 未持久化（仅进程内投递）", event.eventType());
        }
        springPublisher.publishEvent(event);
    }
}
