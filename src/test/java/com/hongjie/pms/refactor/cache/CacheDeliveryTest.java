package com.hongjie.pms.refactor.cache;

import com.hongjie.pms.common.mq.*;
import com.hongjie.pms.common.utils.RedisScanUtil;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.messaging.Message;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class CacheDeliveryTest {
    @Test void failedMqSendReallyEvictsLocallyInsteadOfDoingNothing() {
        var mq=mock(RocketMQTemplate.class);
        RedisTemplate<String,Object> redis=mock(RedisTemplate.class);
        var scan=mock(RedisScanUtil.class);
        when(mq.syncSend(anyString(),any(Message.class))).thenThrow(new IllegalStateException("mq offline"));
        var producer=new CacheUpdateProducer(mq,redis,scan);
        producer.sendEvict("notice","3");
        verify(redis).delete("notice:3");
        producer.sendEvictAll("notice:list");
        verify(scan).scanAndDelete("notice:list:*");
    }
    @Test void failedConsumerDeleteIsRetriedInsteadOfAcknowledged() {
        RedisTemplate<String,Object> redis=mock(RedisTemplate.class);
        var scan=mock(RedisScanUtil.class);
        when(redis.delete("notice:3")).thenThrow(new IllegalStateException("redis offline"));
        assertThatThrownBy(()->new CacheUpdateConsumer(redis,scan).onMessage(
                "{\"operation\":\"EVICT\",\"cacheName\":\"notice\",\"cacheKey\":\"3\"}"))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void failedScanCannotLookLikeAnEmptyCacheRegion() {
        RedisTemplate<String,Object> redis=mock(RedisTemplate.class);
        when(redis.execute(any(RedisCallback.class))).thenThrow(new IllegalStateException("connection lost"));
        assertThatThrownBy(()->new RedisScanUtil(redis).scanAndDelete("activityList_*"))
                .isInstanceOf(IllegalStateException.class);
        verify(redis,never()).delete(anyCollection());
    }
}
