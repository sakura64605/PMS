package com.hongjie.pms.modules.governance.audit.application;

import com.hongjie.pms.modules.governance.audit.application.handler.*;
import com.hongjie.pms.modules.communication.notification.api.service.MessageService;
import com.hongjie.pms.modules.search.api.event.*;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import static org.mockito.Mockito.*;

class AuditChangedListenerTest {
    AuditTargetHandlerFactory factory;AuditTargetHandler handler;MessageService messages;ApplicationEventPublisher events;AuditChangedListener listener;
    @BeforeEach void setup(){
        factory=mock(AuditTargetHandlerFactory.class);handler=mock(AuditTargetHandler.class);
        when(factory.getHandler(anyString())).thenReturn(handler);
        messages=mock(MessageService.class);events=mock(ApplicationEventPublisher.class);
        listener=new AuditChangedListener(factory,messages,events);
    }
    @Test void submitOnlyEvictsCache(){
        listener.changed(new AuditChanged("adopt",1L,null,null,null));
        verify(handler).clearCache(1L);verifyNoInteractions(messages,events);
    }
    @Test void approveKeepsLegacyNotificationArguments(){
        listener.changed(new AuditChanged("adopt",1L,1,null,new AuditWriteTarget.Snapshot(2L,"title","pet_post")));
        verify(messages).sendAuditPassNotification(2L,"title",1L,"pet_post");
        verify(events).publishEvent(any(PetPostUpdatedEvent.class));
    }
    @Test void rejectionPreservesReasonAndActivityIndexEvent(){
        listener.changed(new AuditChanged("activity",1L,2,"reason",new AuditWriteTarget.Snapshot(2L,"title","activity")));
        verify(messages).sendAuditRejectNotification(2L,"title",1L,"activity","reason");
        verify(events).publishEvent(any(ActivityUpdatedEvent.class));
    }
    @Test void dailyAuditStillDoesNotSendNotification(){
        listener.changed(new AuditChanged("daily",1L,1,null,new AuditWriteTarget.Snapshot(2L,"body","daily")));
        verifyNoInteractions(messages);verify(events).publishEvent(any(DailyPostUpdatedEvent.class));
    }
    @Test void failedCacheEvictionDoesNotPreventNotificationOrIndexEvent(){
        doThrow(new IllegalStateException("Redis unavailable")).when(handler).clearCache(1L);
        listener.changed(new AuditChanged("adopt",1L,1,null,new AuditWriteTarget.Snapshot(2L,"title","pet_post")));
        verify(messages).sendAuditPassNotification(2L,"title",1L,"pet_post");
        verify(events).publishEvent(any(PetPostUpdatedEvent.class));
    }
}
