package com.hongjie.pms.modules.communication.notice.application;

import com.hongjie.pms.platform.mq.CacheUpdateProducer;
import com.hongjie.pms.platform.utils.RedisScanUtil;
import com.hongjie.pms.modules.communication.notice.application.listener.NoticeCacheEvictListener;
import com.hongjie.pms.modules.communication.notice.api.event.NoticeChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.*;
import org.springframework.transaction.TransactionDefinition;
import static org.mockito.Mockito.*;

class NoticeCacheEvictListenerTest {
    final CacheUpdateProducer mq = mock(CacheUpdateProducer.class);
    final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    final RedisScanUtil scan = mock(RedisScanUtil.class);
    final NoticeCacheEvictListener listener = new NoticeCacheEvictListener(mq, redis, scan);

    @Test
    void immediateEvictionAndDelayedMqOccurOnlyAfterCommit() {
        var tx =
                new TransactionTemplate(
                        new AbstractPlatformTransactionManager() {
                            protected Object doGetTransaction() {
                                return new Object();
                            }

                            protected void doBegin(Object t, TransactionDefinition d) {}

                            protected void doCommit(DefaultTransactionStatus s) {}

                            protected void doRollback(DefaultTransactionStatus s) {}
                        });
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(TransactionalEventListenerFactory.class);
            context.registerBean(NoticeCacheEvictListener.class, () -> listener);
            context.refresh();
            tx.executeWithoutResult(
                    s -> {
                        context.publishEvent(new NoticeChangedEvent(7L));
                        s.setRollbackOnly();
                    });
            verifyNoInteractions(redis, scan, mq);
            tx.executeWithoutResult(
                    s -> {
                        context.publishEvent(new NoticeChangedEvent(7L));
                        verifyNoInteractions(redis, scan, mq);
                    });
            var order = inOrder(redis, scan, mq);
            order.verify(redis).delete("notice:7");
            order.verify(scan).scanAndDelete("notice:list:*");
            order.verify(mq).sendEvict("notice", "7");
            order.verify(mq).sendEvictAll("notice:list");
        }
    }

    @Test
    void redisFailureDoesNotSuppressListOrDelayedEviction() {
        when(redis.delete("notice:7")).thenThrow(new IllegalStateException("unavailable"));
        listener.evict(7L);
        verify(scan).scanAndDelete("notice:list:*");
        verify(mq).sendEvict("notice", "7");
        verify(mq).sendEvictAll("notice:list");
    }

    @Test
    void nullIdEvictsListsWithoutInventingDetailKey() {
        listener.evict(null);
        verifyNoInteractions(redis);
        verify(scan).scanAndDelete("notice:list:*");
        verify(mq).sendEvictAll("notice:list");
        verifyNoMoreInteractions(mq);
    }
}
