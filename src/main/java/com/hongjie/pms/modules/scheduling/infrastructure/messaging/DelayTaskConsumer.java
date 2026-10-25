package com.hongjie.pms.modules.scheduling.infrastructure.messaging;

import com.alibaba.fastjson2.JSON;
import com.hongjie.pms.platform.mq.DelayMessageDto;
import com.hongjie.pms.modules.scheduling.application.LegacyDelayMessages;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic="delay-task-topic", selectorExpression="delay",
        consumerGroup="delay-task-consumer-group")
public class DelayTaskConsumer implements RocketMQListener<String> {
    private final LegacyDelayMessages tasks;
    @Override
    public void onMessage(String message) {
        tasks.accept(JSON.parseObject(message, DelayMessageDto.class));
    }
}
