package com.hongjie.pms.common.mq;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheUpdateProducer {
    
    private final RocketMQTemplate rocketMQTemplate;
    
    private static final String TOPIC = "cache-update-topic";
    private static final String TAG = "cache";
    
    /**
     * 发送缓存清除消息
     */
    public void sendEvict(String cacheName, String cacheKey) {
        CacheUpdateMessage message = new CacheUpdateMessage();
        message.setOperation("EVICT");
        message.setCacheName(cacheName);
        message.setCacheKey(cacheKey);
        message.setAllEntries(false);
        message.setTimestamp(System.currentTimeMillis());
        
        send(message);
    }
    
    /**
     * 发送清除整个缓存区域的消息
     */
    public void sendEvictAll(String cacheName) {
        CacheUpdateMessage message = new CacheUpdateMessage();
        message.setOperation("EVICT");
        message.setCacheName(cacheName);
        message.setAllEntries(true);
        message.setTimestamp(System.currentTimeMillis());
        
        send(message);
    }

    /**
     * 批量清除多个缓存
     */
    public void evictBatch(String cacheName, String... cacheKeys) {
        for (String cacheKey : cacheKeys) {
            sendEvict(cacheName, cacheKey);
        }
    }
    
    /**
     * 发送延迟清除消息（延迟双删）
     */
    public void sendDelayedEvict(String cacheName, String cacheKey, long delayMillis) {
        CacheUpdateMessage message = new CacheUpdateMessage();
        message.setOperation("EVICT");
        message.setCacheName(cacheName);
        message.setCacheKey(cacheKey);
        message.setAllEntries(false);
        message.setDelayMillis(delayMillis);
        message.setTimestamp(System.currentTimeMillis());
        
        sendDelayed(message, delayMillis);
    }
    
    private void send(CacheUpdateMessage message) {
        try {
            String json = JSON.toJSONString(message);
            Message<String> msg = MessageBuilder.withPayload(json).build();
            rocketMQTemplate.syncSend(TOPIC + ":" + TAG, msg);
            log.debug("缓存更新消息已发送: {}", message);
        } catch (Exception e) {
            log.error("发送缓存更新消息失败，降级为直接清除", e);
            // 降级：直接清除
            handleLocalEvict(message);
        }
    }
    
    private void sendDelayed(CacheUpdateMessage message, long delayMillis) {
        try {
            String json = JSON.toJSONString(message);
            Message<String> msg = MessageBuilder.withPayload(json).build();
            // RocketMQ 延迟级别: 1s, 5s, 10s, 30s, 1m, 2m...
            int delayLevel = getDelayLevel(delayMillis);
            rocketMQTemplate.syncSend(TOPIC + ":" + TAG, msg, 3000, delayLevel);
            log.debug("延迟缓存更新消息已发送: {}, delayLevel={}", message, delayLevel);
        } catch (Exception e) {
            log.error("发送延迟缓存更新消息失败", e);
        }
    }
    
    private int getDelayLevel(long delayMillis) {
        if (delayMillis <= 1000) return 1;      // 1s
        if (delayMillis <= 5000) return 2;      // 5s
        if (delayMillis <= 10000) return 3;     // 10s
        if (delayMillis <= 30000) return 4;     // 30s
        if (delayMillis <= 60000) return 5;     // 1m
        return 5;
    }
    
    /**
     * 降级处理：直接清除本地缓存
     */
    private void handleLocalEvict(CacheUpdateMessage message) {
        // 这里直接调用缓存清除逻辑
        // 可以通过 Spring 上下文获取 Bean
    }
}