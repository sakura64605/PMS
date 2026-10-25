package com.hongjie.pms.modules.governance.audit.application;

import com.hongjie.pms.modules.governance.audit.application.handler.AuditTargetHandlerFactory;
import com.hongjie.pms.modules.communication.notification.api.service.MessageService;
import com.hongjie.pms.modules.search.api.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

/** 仅在提交成功后触发副作用；当前为进程内事件，没有持久化重放保证。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditChangedListener {
    private final AuditTargetHandlerFactory handlers;
    private final MessageService messages;
    private final ApplicationEventPublisher events;
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void changed(AuditChanged event){
        run("缓存失效",event,()->handlers.getHandler(event.type()).clearCache(event.targetId()));
        if(event.status()==null)return;
        var target=event.target();
        // 日常审核原本没有站内通知，保留此行为。
        if(!"daily".equals(target.notificationType()))run("审核通知",event,()->{
            if(event.status()==1) messages.sendAuditPassNotification(target.ownerId(),target.title(),event.targetId(),target.notificationType());
            else messages.sendAuditRejectNotification(target.ownerId(),target.title(),event.targetId(),target.notificationType(),event.reason());
        });
        run("搜索同步事件",event,()->{
            switch(target.notificationType()){
                case "pet_post" -> events.publishEvent(new PetPostUpdatedEvent(this,event.targetId(),"audit"));
                case "activity" -> events.publishEvent(new ActivityUpdatedEvent(this,event.targetId(),"audit"));
                case "daily" -> events.publishEvent(new DailyPostUpdatedEvent(this,event.targetId(),"audit"));
                default -> throw new IllegalStateException("未知索引类型");
            }
        });
    }
    private void run(String action,AuditChanged event,Runnable task){
        try{task.run();}catch(RuntimeException e){log.error("审核已提交但{}失败: type={}, id={}",action,event.type(),event.targetId(),e);}
    }
}
