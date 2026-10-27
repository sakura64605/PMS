package com.hongjie.pms.modules.community.following.application;

import com.hongjie.pms.modules.communication.notification.api.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class FollowCreatedListener {
    private final MessageService messages;
    private final PlatformTransactionManager transactionManager;

    @TransactionalEventListener
    public void created(FollowCreated event) {
        try {
            // MQ failure may fall back to a local database insert. The committed
            // relationship transaction must not be reused for that write.
            var transaction = new TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.executeWithoutResult(
                    status ->
                            messages.sendFollowNotification(
                                    event.followingId(),
                                    event.followerId(),
                                    event.followerName(),
                                    "/user/" + event.followerId()));
        } catch (RuntimeException e) {
            log.error(
                    "关注已提交，通知发送失败: followerId={}, followingId={}",
                    event.followerId(),
                    event.followingId(),
                    e);
        }
    }
}
