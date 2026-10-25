package com.hongjie.pms.modules.scheduling.application;
import com.hongjie.pms.platform.mq.DelayMessageDto;
import com.hongjie.pms.modules.scheduling.infrastructure.persistence.entity.DelayTask;
import com.hongjie.pms.modules.scheduling.infrastructure.persistence.mapper.DelayTaskMapper;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.Activity;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.ActivityMapper;
import com.hongjie.pms.modules.scheduling.infrastructure.messaging.DelayTaskConsumer;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class LegacyDelayMessagesTest {
    final ScheduledTaskMapper locks=mock(ScheduledTaskMapper.class);
    final ActivityMapper activities=mock(ActivityMapper.class);
    final DelayTaskMapper tasks=mock(DelayTaskMapper.class);
    final LegacyDelayMessages service=new LegacyDelayMessages(locks,activities,tasks);
    @Test void rejectsUnsupportedTaskBeforeDatabaseAccess(){
        assertThatThrownBy(()->service.accept(DelayMessageDto.builder().type("UNKNOWN").businessId(1L).build()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(locks,activities,tasks);
    }
    @Test void staleMessageUsesCurrentActivityTimeAndExistingTaskIsNotDuplicated(){
        var activity=new Activity();activity.setStartTime(LocalDateTime.of(2026,11,1,12,0));
        when(locks.lockActivity(1L)).thenReturn(1L);when(activities.selectById(1L)).thenReturn(activity);
        when(tasks.selectCount(any())).thenReturn(0L,1L);
        var message=DelayMessageDto.builder().type("ACTIVITY_REMIND").businessId(1L).executeTime(1L).build();
        service.accept(message);service.accept(message);
        var captured=org.mockito.ArgumentCaptor.forClass(DelayTask.class);verify(tasks).insert(captured.capture());
        assertThat(captured.getValue().getExecuteTime()).isEqualTo(activity.getStartTime().minusMinutes(30));
        var order=inOrder(locks,activities,tasks);order.verify(locks).lockActivity(1L);order.verify(activities).selectById(1L);
    }
    @Test void deletedActivityIsAcknowledgedWithoutCreatingTask(){
        when(locks.lockActivity(1L)).thenReturn(null);
        service.accept(DelayMessageDto.builder().type("ACTIVITY_STATISTICS").businessId(1L).build());
        verifyNoInteractions(activities,tasks);
    }
    @Test void consumerPropagatesFailuresForBrokerRetry(){
        var handler=mock(LegacyDelayMessages.class);doThrow(new IllegalStateException("db unavailable")).when(handler).accept(any());
        assertThatThrownBy(()->new DelayTaskConsumer(handler).onMessage("{\"type\":\"ACTIVITY_REMIND\",\"businessId\":1}"))
                .isInstanceOf(IllegalStateException.class);
    }
}
