package com.hongjie.pms.refactor.activity;

import com.hongjie.pms.modules.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityParticipationListener {
    private final MessageService messages;
    private final PlatformTransactionManager transactionManager;

    @TransactionalEventListener
    public void changed(ActivityParticipationChanged event) {
        if(event.kind()==ActivityParticipationChanged.Kind.CHECKED_IN) {
            send(event,()->messages.sendSignInSuccessNotification(event.participantId(),event.title(),event.activityId()));
            return;
        }
        if(event.full()) send(event,()->messages.sendActivityFullNotification(event.ownerId(),event.title(),event.activityId()));
        send(event,()->messages.sendSomeoneSignUpNotification(event.ownerId(),event.participantId(),event.name(),event.title(),event.activityId()));
        send(event,()->messages.sendSignUpSuccessNotification(event.participantId(),event.title(),event.activityId()));
    }
    private void send(ActivityParticipationChanged event,Runnable notification) {
        try {
            var tx=new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.executeWithoutResult(status->notification.run());
        } catch(RuntimeException failure) {
            log.error("活动参与已提交，通知失败: activityId={}, participantId={}",event.activityId(),event.participantId(),failure);
        }
    }
}
