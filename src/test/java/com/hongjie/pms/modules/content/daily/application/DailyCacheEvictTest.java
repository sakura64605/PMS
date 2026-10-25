package com.hongjie.pms.modules.content.daily.application;

import com.hongjie.pms.platform.utils.RedisScanUtil;
import com.hongjie.pms.modules.content.daily.api.service.DailyCacheInvalidator;
import com.hongjie.pms.modules.content.daily.application.listener.DailyCacheEvictListener;
import com.hongjie.pms.modules.community.comment.api.event.CommentCreatedEvent;
import com.hongjie.pms.shared.core.enums.BizTargetType;
import com.hongjie.pms.modules.governance.audit.application.AuditChanged;
import com.hongjie.pms.modules.governance.report.application.ReportTargetChanged;
import com.hongjie.pms.modules.community.like.application.LikeChanged;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.*;
import org.springframework.transaction.TransactionDefinition;
import static org.mockito.Mockito.*;

class DailyCacheEvictTest {
    final RedisScanUtil scan = mock(RedisScanUtil.class);
    final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    final DailyCacheInvalidator cache = new DailyCacheInvalidator(scan, redis);
    final TransactionTemplate tx = new TransactionTemplate(new AbstractPlatformTransactionManager() {
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object t, TransactionDefinition d) {}
        protected void doCommit(DefaultTransactionStatus s) {}
        protected void doRollback(DefaultTransactionStatus s) {}
    });

    @Test void writesEvictActualPagePatternsOnlyAfterCommitAndNeverOnRollback() {
        tx.executeWithoutResult(s -> { cache.allAfterCommit(); verifyNoInteractions(scan, redis); s.setRollbackOnly(); });
        verifyNoInteractions(scan, redis);
        tx.executeWithoutResult(s -> { cache.allAfterCommit(); verifyNoInteractions(scan, redis); });
        verify(scan).scanAndDelete("daily:rec:*");
        verify(scan).scanAndDelete("rec:daily:*");
        verify(redis).delete("topic:hot");
    }

    @Test void actionsEvictAllPagesOfOnlyThatViewer() {
        tx.executeWithoutResult(s -> { cache.viewerAfterCommit(12L); verifyNoInteractions(scan); });
        verify(scan).scanAndDelete("daily:rec:12:*");
        verify(scan).scanAndDelete("rec:daily:12:*");
        verifyNoMoreInteractions(scan);
        verifyNoInteractions(redis);
    }

    @Test void domainEventsEvictOnCommitIncludingSelfCommentsButNotOtherDomainsOrRollback() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(TransactionalEventListenerFactory.class);
            context.registerBean(DailyCacheEvictListener.class, () -> new DailyCacheEvictListener(cache));
            context.refresh();
            Object[] events = {
                new CommentCreatedEvent(1L, BizTargetType.DAILY, 3L),
                new LikeChanged(2L, 3L, "daily", null, false),
                new ReportTargetChanged(BizTargetType.DAILY, 3L),
                new AuditChanged("daily", 3L, 1, null, null)
            };
            for (Object event : events) {
                clearInvocations(scan, redis);
                tx.executeWithoutResult(s -> { context.publishEvent(event); verifyNoInteractions(scan, redis); s.setRollbackOnly(); });
                verifyNoInteractions(scan, redis);
                tx.executeWithoutResult(s -> { context.publishEvent(event); verifyNoInteractions(scan, redis); });
                verify(scan).scanAndDelete("daily:rec:*");
                verify(scan).scanAndDelete("rec:daily:*");
            }
            clearInvocations(scan, redis);
            tx.executeWithoutResult(s -> context.publishEvent(new CommentCreatedEvent(1L, BizTargetType.PET_POST, 3L)));
            context.publishEvent(events[0]);
            verifyNoInteractions(scan, redis);
        }
    }
}
