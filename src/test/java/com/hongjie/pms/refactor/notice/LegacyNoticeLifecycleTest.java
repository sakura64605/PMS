package com.hongjie.pms.refactor.notice;

import com.hongjie.pms.common.mq.CacheUpdateProducer;
import com.hongjie.pms.modules.notice.mapper.NoticeMapper;
import com.hongjie.pms.modules.notice.mapper.NoticeReadRecordMapper;
import com.hongjie.pms.modules.notice.service.Impl.NoticeServiceImpl;
import com.hongjie.pms.modules.notice.service.NoticeService;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyNoticeLifecycleTest {
    @Configuration(proxyBeanMethods = false)
    @Import(NoticeServiceImpl.class)
    static class Legacy {
        @Bean NoticeMapper notices() { return mock(NoticeMapper.class); }
        @Bean NoticeReadRecordMapper reads() { return mock(NoticeReadRecordMapper.class); }
        @Bean CacheUpdateProducer cache() { return mock(CacheUpdateProducer.class); }
        @Bean RedisTemplate<?, ?> redis() { return mock(RedisTemplate.class); }
    }

    @Configuration(proxyBeanMethods = false)
    static class Replacement {
        @Bean @Primary NoticeService replacement() { return mock(NoticeService.class); }
    }

    @Test void replacementDoesNotTouchRedisOrCreateWorker() {
        var redisson = mock(RedissonClient.class);
        new ApplicationContextRunner().withUserConfiguration(Legacy.class, Replacement.class)
                .withBean(RedissonClient.class, () -> redisson).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(NoticeServiceImpl.class);
                    verifyNoInteractions(redisson);
                });
    }

    @Test void legacyOnlyStartsOnceAndContextCloseTerminatesWorker() throws Exception {
        var redisson = mock(RedissonClient.class);
        @SuppressWarnings("unchecked") RBlockingQueue<Long> queue = mock(RBlockingQueue.class);
        when(redisson.<Long>getBlockingQueue("notice:delay:queue")).thenReturn(queue);
        var entered = new CountDownLatch(1);
        when(queue.take()).thenAnswer(invocation -> {
            entered.countDown();
            new CountDownLatch(1).await();
            return null;
        });
        Thread[] worker = new Thread[1];
        new ApplicationContextRunner().withUserConfiguration(Legacy.class).withPropertyValues("spring.profiles.active=legacy-reference")
                .withBean(RedissonClient.class, () -> redisson).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
                    var legacy = context.getBean(NoticeServiceImpl.class);
                    assertThat(context.getBean(NoticeService.class)).isSameAs(legacy);
                    assertThat(legacy.isRunning()).isTrue();
                    worker[0] = (Thread) ReflectionTestUtils.getField(legacy, "consumerThread");
                    assertThat(worker[0].isDaemon()).isTrue();
                    legacy.start();
                    verify(redisson, times(1)).getBlockingQueue("notice:delay:queue");
                });
        assertThat(worker[0].isAlive()).isFalse();
    }
}
