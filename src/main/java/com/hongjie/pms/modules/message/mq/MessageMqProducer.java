package com.hongjie.pms.modules.message.mq;

import com.alibaba.fastjson2.JSON;
import com.hongjie.pms.common.utils.SpringContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageMqProducer {

    private final RocketMQTemplate rocketMQTemplate;

    private static final String TOPIC = "message-topic";
    private static final String TAG = "push";

    /**
     * 发送消息到 MQ
     */
    public void send(MessageMqDto dto) {
        try {
            Message<String> message = MessageBuilder
                    .withPayload(JSON.toJSONString(dto))
                    .build();

            long start = System.currentTimeMillis();
            rocketMQTemplate.syncSend(TOPIC + ":" + TAG, message);
            log.info("MQ 发送耗时: {} ms", System.currentTimeMillis() - start);
            log.info("消息已发送到MQ: userId={}, type={}", dto.getUserId(), dto.getType());
        } catch (Exception e) {
            log.error("MQ发送失败，降级为直接处理: {}", e.getMessage());
            // 降级：直接调用本地处理
            handleLocally(dto);
        }
    }

    /**
     * 发送延迟消息（用于活动提醒）
     * @param dto 消息内容
     * @param delayLevel 延迟级别：1=1s, 2=5s, 3=10s, 4=30s, 5=1m, 6=2m, 7=3m, 8=4m, 9=5m, 10=6m, 11=7m, 12=8m, 13=9m, 14=10m, 15=20m, 16=30m, 17=1h, 18=2h
     */
    public void sendDelay(MessageMqDto dto, int delayLevel) {
        try {
            Message<String> message = MessageBuilder
                    .withPayload(JSON.toJSONString(dto))
                    .build();
            
            rocketMQTemplate.syncSend(TOPIC + ":" + TAG, message, 3000, delayLevel);
            log.info("延迟消息已发送到MQ: userId={}, type={}, delayLevel={}", 
                     dto.getUserId(), dto.getType(), delayLevel);
        } catch (Exception e) {
            log.error("MQ延迟消息发送失败: {}", e.getMessage());
            handleLocally(dto);
        }
    }

    /**
     * 降级处理：直接调用本地存储和推送
     */
    private void handleLocally(MessageMqDto dto) {
        // 这里直接调用 MessageService 的本地处理方法
        // 需要通过 Spring 上下文获取 Bean，避免循环依赖
        SpringContextHolder.getBean(MessageMqConsumer.class).handleMessage(dto);
    }
}