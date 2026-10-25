package com.hongjie.pms.refactor.message;

import com.hongjie.pms.modules.message.entity.UserMessage;
import com.hongjie.pms.modules.message.mapper.UserMessageMapper;
import com.hongjie.pms.modules.message.mq.*;
import com.hongjie.pms.refactor.message.api.MessageService;
import com.hongjie.pms.refactor.extension.notify.handler.*;
import com.hongjie.pms.refactor.extension.target.TargetTypeCodec;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageCompatibilityTest {
    @Test void everyLegacyPublicEntryIsOverridden()throws Exception{
        for(var method:MessageService.class.getDeclaredMethods())if(Modifier.isPublic(method.getModifiers()))
            assertThat(PmsMessageService.class.getDeclaredMethod(method.getName(),method.getParameterTypes()).getDeclaringClass()).isEqualTo(PmsMessageService.class);
    }
    @Test void notificationTemplatesPreserveExistingPayloads(){
        var producer=mock(MessageMqProducer.class);var mapper=mock(UserMessageMapper.class);List<MessageMqDto> expected=new ArrayList<>();List<MessageMqDto> actual=new ArrayList<>();
        doAnswer(call->{expected.add(call.getArgument(0));return null;}).when(producer).send(any());
        ApplicationEventPublisher publisher=event->actual.add(((PendingNotification)event).toMq());
        var delivery=new MessageDelivery(publisher,producer,mock(PlatformTransactionManager.class));
        var reference=new com.hongjie.pms.modules.message.service.MessageService(mapper,producer);
        MessageService legacy=(MessageService)java.lang.reflect.Proxy.newProxyInstance(MessageService.class.getClassLoader(),new Class<?>[]{MessageService.class},
            (proxy,method,args)->reference.getClass().getMethod(method.getName(),method.getParameterTypes()).invoke(reference,args));
        MessageService replacement=new PmsMessageService(mock(MessageInbox.class),delivery,new LikeNotificationHandler(),new CommentNotificationHandler(),new TargetTypeCodec());
        List<Consumer<MessageService>> cases=new ArrayList<>();
        for(String type:List.of("pet_post","pet_comment","pet_activity","daily","系统通知","unknown")){
            cases.add(service->service.sendLikeNotification(1L,2L,type,"title",10L,"/link"));
            cases.add(service->service.sendCommentNotification(1L,2L,type,"title","comment".repeat(30),10L,"/link"));
        }
        cases.add(service->service.sendFollowNotification(1L,2L,"sender","/user/2"));
        cases.add(service->service.sendSignUpSuccessNotification(1L,"title",10L));
        cases.add(service->service.sendSomeoneSignUpNotification(1L,2L,"sender","title",10L));
        cases.add(service->service.sendSignInSuccessNotification(1L,"title",10L));
        cases.add(service->service.sendActivityReminder(1L,"title",10L,30));
        cases.add(service->service.sendActivityFullNotification(1L,"title",10L));
        cases.add(service->service.sendAuditPassNotification(1L,"title",10L,"activity"));
        cases.add(service->service.sendAuditRejectNotification(1L,"title",10L,"activity","reason"));
        cases.add(service->service.sendPunishmentStartNotification(1L,"restricted"));
        cases.add(service->service.sendPunishmentEndNotification(1L));
        cases.add(service->service.sendActivityStatisticsNotification(1L,"title",10L,10,8,2));
        for(var action:cases){action.accept(legacy);action.accept(replacement);}
        assertThat(actual).hasSize(cases.size());for(int i=0;i<actual.size();i++)assertThat(actual.get(i)).usingRecursiveComparison().ignoringFields("createTime").isEqualTo(expected.get(i));
    }
    @Test void enqueuedEntityIsAnImmutableSnapshot(){
        List<PendingNotification> events=new ArrayList<>();var delivery=new MessageDelivery(event->events.add((PendingNotification)event),mock(MessageMqProducer.class),mock(PlatformTransactionManager.class));
        var message=new UserMessage();message.setUserId(1L);message.setType("SYSTEM");message.setContent("original");
        delivery.enqueue(delivery.snapshot(message));message.setUserId(2L);message.setContent("changed");assertThat(events.get(0).userId()).isEqualTo(1L);assertThat(events.get(0).content()).isEqualTo("original");
    }
}
