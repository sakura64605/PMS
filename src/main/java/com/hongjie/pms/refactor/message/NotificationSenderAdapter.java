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
 * v2 重写一遍只会引入新的不一致。所以这里做的是<b>适配</b> ——
 * 把新的值对象翻译成老的实体，走同一条管道，因此新老通知的可靠性与行为完全一致。
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
