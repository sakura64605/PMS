package com.hongjie.pms.common.mq;

import com.alibaba.fastjson2.JSON;
import com.hongjie.pms.common.mq.CacheUpdateMessage;
import com.hongjie.pms.common.utils.RedisScanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "cache-update-topic",
        selectorExpression = "cache",
        consumerGroup = "cache-update-consumer-group"
)
public class CacheUpdateConsumer implements RocketMQListener<String> {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScanUtil redisScanUtil;

    @Override
    public void onMessage(String message) {
        try {
            CacheUpdateMessage msg = JSON.parseObject(message, CacheUpdateMessage.class);
            log.debug("收到缓存更新消息: {}", msg);
            
            // 延迟处理
            if (msg.getDelayMillis() != null && msg.getDelayMillis() > 0) {
                Thread.sleep(msg.getDelayMillis());
            }
            
            if ("EVICT".equals(msg.getOperation())) {
                if (msg.isAllEntries()) {
                    // 清除整个缓存区域（使用 SCAN 替代 keys()，避免阻塞 Redis）
                    String pattern = msg.getCacheName() + ":*";
                    long deleted = redisScanUtil.scanAndDelete(pattern);
                    log.info("清除缓存区域: {}, 共{}个key", msg.getCacheName(), deleted);
                } else {
                    // 清除单个缓存
                    String key = msg.getCacheName() + ":" + msg.getCacheKey();
                    redisTemplate.delete(key);
                    log.debug("清除缓存: {}", key);
                }
            }
        } catch (Exception e) {
            log.error("处理缓存更新消息失败", e);
        }
    }
}