package com.hongjie.pms.common.mq;

import com.alibaba.fastjson2.JSON;
import com.hongjie.pms.common.mq.CacheUpdateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

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
                    // 清除整个缓存区域
                    String pattern = msg.getCacheName() + ":*";
                    Set<String> keys = redisTemplate.keys(pattern);
                    if (keys != null && !keys.isEmpty()) {
                        redisTemplate.delete(keys);
                        log.info("清除缓存区域: {}, 共{}个key", msg.getCacheName(), keys.size());
                    }
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