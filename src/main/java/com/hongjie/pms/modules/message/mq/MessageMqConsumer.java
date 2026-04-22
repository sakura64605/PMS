package com.hongjie.pms.modules.message.mq;

import com.alibaba.fastjson2.JSON;
import com.hongjie.pms.common.circuitbreaker.annotation.CircuitBreaker;
import com.hongjie.pms.modules.message.entity.UserMessage;
import com.hongjie.pms.modules.message.mapper.UserMessageMapper;
import com.hongjie.pms.modules.message.websocket.WebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "message-topic",
        selectorExpression = "push",
        consumerGroup = "message-consumer-group"
)
public class MessageMqConsumer implements RocketMQListener<String> {

    private final UserMessageMapper messageMapper;

    @Override
    public void onMessage(String message) {
        try {
            MessageMqDto dto = JSON.parseObject(message, MessageMqDto.class);
            handleMessage(dto);
        } catch (Exception e) {
            log.error("消费消息失败: {}", e.getMessage(), e);
            throw new RuntimeException(e);  // 抛出异常触发重试
        }
    }

    /**
     * 处理消息：存储 + 推送
     */
    @CircuitBreaker(
            value = "mqConsume",
            windowSize = 10,
            minRequestAmount = 5,
            errorRateThreshold = 0.5,
            openDurationSeconds = 10,
            fallbackMethod = "fallbackHandleMessage"
    )
    public void handleMessage(MessageMqDto dto) {
        // 1. 存储到数据库
        UserMessage userMessage = new UserMessage();
        userMessage.setUserId(dto.getUserId());
        userMessage.setSenderId(dto.getSenderId());
        userMessage.setType(dto.getType());
        userMessage.setTitle(dto.getTitle());
        userMessage.setContent(dto.getContent());
        userMessage.setBusinessId(dto.getBusinessId());
        userMessage.setLink(dto.getLink());
        userMessage.setIsRead(0);
        userMessage.setCreateTime(dto.getCreateTime() != null ? dto.getCreateTime() : LocalDateTime.now());
        
        messageMapper.insert(userMessage);
        log.info("消息已存储: userId={}, type={}", dto.getUserId(), dto.getType());

        // 2. WebSocket 实时推送
        boolean pushed = WebSocketHandler.pushToUser(dto.getUserId(), userMessage);
        if (pushed) {
            log.debug("消息已推送: userId={}", dto.getUserId());
        } else {
            log.debug("用户不在线，消息已存储: userId={}", dto.getUserId());
        }
    }

    /**
     * 降级方法：只存储，不推送
     */
    public void fallbackHandleMessage(MessageMqDto dto) {
        log.warn("消息消费熔断降级，只存储不推送: userId={}, type={}", dto.getUserId(), dto.getType());

        // 降级：只存数据库，不推送 WebSocket
        UserMessage userMessage = new UserMessage();
        userMessage.setUserId(dto.getUserId());
        userMessage.setSenderId(dto.getSenderId());
        userMessage.setType(dto.getType());
        userMessage.setTitle(dto.getTitle());
        userMessage.setContent(dto.getContent());
        userMessage.setBusinessId(dto.getBusinessId());
        userMessage.setLink(dto.getLink());
        userMessage.setIsRead(0);
        userMessage.setCreateTime(LocalDateTime.now());

        messageMapper.insert(userMessage);
        log.info("降级处理：消息已存储，未推送: userId={}", dto.getUserId());
    }

    public void fallbackHandleMessage(MessageMqDto dto, Exception e) {
        log.error("消息消费熔断降级: userId={}, error={}", dto.getUserId(), e.getMessage());
        fallbackHandleMessage(dto);
    }
}