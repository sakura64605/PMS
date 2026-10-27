package com.hongjie.pms.modules.communication.notification.application;

import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.communication.notification.infrastructure.persistence.entity.UserMessage;
import com.hongjie.pms.modules.communication.notification.infrastructure.messaging.MessageMqProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageDelivery {
    private final ApplicationEventPublisher events;
    private final MessageMqProducer producer;
    private final PlatformTransactionManager transactionManager;

    public PendingNotification snapshot(UserMessage message) {
        if (message == null) throw new BusinessException(ErrorCode.PARAM_ERROR);
        return new PendingNotification(
                message.getUserId(),
                message.getSenderId(),
                message.getType(),
                message.getTitle(),
                message.getContent(),
                message.getBusinessId(),
                message.getLink(),
                LocalDateTime.now());
    }

    public void validate(PendingNotification message) {
        if (message.userId() == null
                || message.userId() <= 0
                || !StringUtils.hasText(message.type())
                || message.type().length() > 20
                || message.content() == null
                || message.title() != null && message.title().length() > 100
                || message.link() != null && message.link().length() > 255)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "通知参数不符合消息模型");
    }

    public void enqueue(PendingNotification message) {
        validate(message);
        events.publishEvent(message);
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void deliver(PendingNotification message) {
        try {
            // Existing MQ fallback writes the DB synchronously. Use a fresh transaction
            // both after commit and when called by another AFTER_COMMIT listener.
            var tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.executeWithoutResult(status -> producer.send(message.toMq()));
        } catch (RuntimeException e) {
            log.error(
                    "站内通知投递失败: recipient={}, type={}, businessId={}",
                    message.userId(),
                    message.type(),
                    message.businessId(),
                    e);
        }
    }
}
