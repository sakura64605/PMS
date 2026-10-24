package com.hongjie.pms.refactor.cache;

import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.utils.RedisScanUtil;
import com.hongjie.pms.refactor.comment.event.CommentCreatedEvent;
import com.hongjie.pms.refactor.comment.listener.CommentCacheListener;
import com.hongjie.pms.refactor.core.cache.AfterCommitCache;
import com.hongjie.pms.refactor.core.enums.BizTargetType;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class CacheCommitTest {
    static class Transactions extends AbstractPlatformTransactionManager {
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction, TransactionDefinition definition) {}
        protected void doCommit(DefaultTransactionStatus status) {}
        protected void doRollback(DefaultTransactionStatus status) {}
    }
    @Test void evictionWaitsForCommitAndIsDiscardedOnRollback() {
        Runnable eviction = mock(Runnable.class);
        var tx = new TransactionTemplate(new Transactions());
        tx.executeWithoutResult(status -> {
            AfterCommitCache.evict(eviction);
            verifyNoInteractions(eviction);
        });
        verify(eviction).run();
        clearInvocations(eviction);
        tx.executeWithoutResult(status -> {
            AfterCommitCache.evict(eviction);
            status.setRollbackOnly();
        });
        verifyNoInteractions(eviction);
    }
    @Test void evictionOutsideTransactionRunsImmediately() {
        Runnable eviction = mock(Runnable.class);
        AfterCommitCache.evict(eviction);
        verify(eviction).run();
    }
    @Test void cacheOutageDoesNotReportACommittedToggleAsFailed() {
        var tx = new TransactionTemplate(new Transactions());
        assertThatCode(() -> tx.executeWithoutResult(status ->
                AfterCommitCache.evict(() -> { throw new IllegalStateException("redis offline"); })))
                .doesNotThrowAnyException();
    }
    @Test void commentEventEvictsOnlyAfterSuccessfulCommitIncludingSelfComments() {
        var cache = mock(DistributedCache.class);
        var scan = mock(RedisScanUtil.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(TransactionalEventListenerFactory.class);
            context.registerBean(CommentCacheListener.class, () -> new CommentCacheListener(cache, scan));
            context.refresh();
            var tx = new TransactionTemplate(new Transactions());
            tx.executeWithoutResult(status -> {
                context.publishEvent(new CommentCreatedEvent(1L, BizTargetType.PET_POST, 10L));
                verifyNoInteractions(cache, scan);
            });
            verify(cache).delete("pet_10");
            clearInvocations(cache);
            tx.executeWithoutResult(status -> {
                context.publishEvent(new CommentCreatedEvent(2L, BizTargetType.PET_ACTIVITY, 12L));
                status.setRollbackOnly();
            });
            verifyNoInteractions(cache, scan);
            tx.executeWithoutResult(status -> context.publishEvent(
                    new CommentCreatedEvent(3L, BizTargetType.PET_ACTIVITY, 12L)));
            verify(cache).delete("activity_12");
            verify(scan).scanAndDelete("activityList_*");
        }
    }
}
