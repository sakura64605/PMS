package com.hongjie.pms.refactor.notice.event;

import com.hongjie.pms.refactor.core.event.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 公告发生了会改变缓存内容的变更。
 *
 * <p><b>为什么要做成事件而不是直接调缓存清除</b>：老代码在
 * {@code @Transactional} 方法体里直接调 {@code cacheUpdateProducer.sendEvictAll(...)}，
 * 也就是<b>事务还没提交就把缓存清掉了</b>。后果是缓存清除与数据库提交之间存在窗口：
 * 清完缓存后、提交前若有请求进来，会把「旧的」数据重新读进缓存 ——
 * 等事务提交后，缓存里反而是过期的内容，而且要等 TTL 到期才会自愈。
 *
 * <p>改成发布事件、由 {@code @TransactionalEventListener(AFTER_COMMIT)} 的监听器执行清除，
 * 顺序就变成「先提交、后清缓存」，那个窗口消失了。事务回滚时监听器根本不执行，
 * 缓存也不会被无谓地清掉。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class NoticeChangedEvent extends DomainEvent {

    private Long noticeId;

    @Override
    public String eventType() {
        return "NoticeChanged";
    }

    @Override
    public String aggregateType() {
        return "Notice";
    }

    @Override
    public Long aggregateId() {
        return noticeId;
    }
}
