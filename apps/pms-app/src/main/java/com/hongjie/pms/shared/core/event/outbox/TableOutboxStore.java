package com.hongjie.pms.shared.core.event.outbox;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hongjie.pms.shared.core.event.DomainEvent;
import com.hongjie.pms.shared.core.event.OutboxStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 基于 {@code pms_domain_event} 表的发件箱实现。
 *
 * <p>装配后，{@code DomainEventPublisher} 会自动从「仅进程内投递」升级为
 * 「事务内落库 + 提交后投递」，因为它按 {@code ObjectProvider} 查找本类。
 *
 * <p><b>默认关闭</b>：本类依赖新增表 {@code pms_domain_event}，而该表需要先执行
 * {@code src/main/resources/db/core-v2.sql} 才会存在。若默认启用，
 * 任何一次事件发布都会因表不存在而插入失败 —— 这个失败会连带把业务事务一起回滚
 * （因为发件箱写入与业务写操作同事务），也就是「发一条评论失败」。
 * 因此这里要求显式开启：
 * <pre>
 *   pms:
 *     event:
 *       outbox:
 *         enabled: true
 * </pre>
 * 未开启时，{@code DomainEventPublisher} 只会打一条警告并做进程内投递，
 * 依赖 {@code @TransactionalEventListener(AFTER_COMMIT)} 的监听器照常工作 ——
 * 通知功能不受影响，只是失去了「进程崩溃也不丢事件」的持久化保证。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pms.event.outbox.enabled", havingValue = "true")
public class TableOutboxStore implements OutboxStore {

    private final PmsDomainEventMapper mapper;

    @Override
    public void append(DomainEvent event) {
        PmsDomainEvent record = new PmsDomainEvent();
        record.setEventId(event.getEventId());
        record.setEventType(event.eventType());
        record.setPayloadClass(event.getClass().getName());
        record.setAggregateType(event.aggregateType());
        record.setAggregateId(event.aggregateId());
        record.setPayload(JSON.toJSONString(event));
        record.setStatus(OutboxStatus.PENDING.getCode());
        record.setRetryCount(0);
        mapper.insert(record);

        log.debug("事件已写入发件箱: eventId={}, type={}", event.getEventId(), event.eventType());
    }

    @Override
    public void markSent(String eventId) {
        mapper.update(
                null,
                new LambdaUpdateWrapper<PmsDomainEvent>()
                        .eq(PmsDomainEvent::getEventId, eventId)
                        .set(PmsDomainEvent::getStatus, OutboxStatus.SENT.getCode())
                        .set(PmsDomainEvent::getSentAt, LocalDateTime.now()));
    }

    @Override
    public void markFailed(String eventId, String reason) {
        // 重试次数与下次重试时间的退避计算放在兜底重投任务里统一处理，
        // 这里只记录失败状态与原因，避免在两处各写一套退避策略。
        mapper.update(
                null,
                new LambdaUpdateWrapper<PmsDomainEvent>()
                        .eq(PmsDomainEvent::getEventId, eventId)
                        .set(PmsDomainEvent::getStatus, OutboxStatus.FAILED.getCode())
                        .set(PmsDomainEvent::getLastError, truncate(reason)));

        log.warn("事件投递失败: eventId={}, reason={}", eventId, reason);
    }

    /** last_error 列长度 500，截断避免写入失败反而掩盖真实错误 */
    private String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }
}
