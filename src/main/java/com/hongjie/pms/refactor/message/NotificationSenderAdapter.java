package com.hongjie.pms.refactor.message;

import com.hongjie.pms.refactor.extension.notify.NotificationMessage;
import com.hongjie.pms.refactor.extension.notify.NotificationSender;
import com.hongjie.pms.modules.message.entity.UserMessage;
import com.hongjie.pms.modules.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link NotificationSender} 的适配器 —— 依赖倒置的另外一半。
 *
 * <p>通用扩展层只声明「投递一条通知」这个能力，由本类把它接到既有的
 * {@code MessageService.sendAndPush} 上。
 *
 * <p><b>为什么复用老的发送链路而不是另起一套</b>：老链路
 * （{@code MessageService -> MessageMqProducer -> MessageMqConsumer -> WebSocketHandler}）
 * 已经带了 MQ 重试、熔断降级、离线存储，是经过线上验证的。
 * 本适配器把策略生成的值对象翻译为既有实体。当前由 @Primary 的
 * PmsMessageService 接管 MessageService 注入，在业务事务提交后再进入同一 MQ 管道；
 * 无活动事务时直接投递。MQ 消费、重试与熔断能力仍沿用旧链路，不宣称恰好一次投递。
 */
@Component
@RequiredArgsConstructor
public class NotificationSenderAdapter implements NotificationSender {

    private final MessageService messageService;

    @Override
    public void send(NotificationMessage message) {
        UserMessage entity = new UserMessage();
        entity.setUserId(message.userId());
        entity.setSenderId(message.senderId());
        entity.setType(message.type().getCode());
        entity.setTitle(message.title());
        entity.setContent(message.content());
        entity.setBusinessId(message.businessId());
        entity.setLink(message.link());

        messageService.sendAndPush(entity);
    }
}
