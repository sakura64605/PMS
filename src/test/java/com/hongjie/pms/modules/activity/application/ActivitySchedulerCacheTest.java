package com.hongjie.pms.modules.activity.application;

import com.hongjie.pms.modules.scheduling.api.*;
import com.hongjie.pms.modules.scheduling.infrastructure.persistence.entity.*;
import com.hongjie.pms.modules.scheduling.infrastructure.persistence.mapper.*;
import com.hongjie.pms.modules.scheduling.application.DelayTaskService;
import com.hongjie.pms.modules.activity.infrastructure.cache.ActivityCache;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.*;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.*;
import com.hongjie.pms.modules.communication.notification.api.service.MessageService;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.modules.identity.user.domain.policy.PunishmentUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivitySchedulerCacheTest {
    @Mock ActivityMapper mapper;
    @Mock ActivitySignupMapper signups;
    @Mock ActivityCache cache;
    @Mock MessageService messages;
    @Mock UserMapper users;
    @Mock PunishmentUtil punishment;
    @Mock DelayTaskService delay;
    @InjectMocks ActivityStatusScheduler scheduler;
    @Mock com.hongjie.pms.modules.scheduling.application.ScheduledTaskMapper locks;
    @Mock com.hongjie.pms.modules.identity.user.application.UserCache userCache;
    @InjectMocks com.hongjie.pms.modules.scheduling.application.ScheduledBusinessTasks handler;

    Activity activity(int status) {
        var activity = new Activity(); activity.setId(7L); activity.setStatus(status); activity.setDeleted(0); activity.setAuditStatus(1);
        activity.setStartTime(LocalDateTime.now().minusHours(1)); activity.setEndTime(LocalDateTime.now().minusMinutes(2));
        activity.setCurrentPeople(5); activity.setMaxPeople(10); activity.setTitle("fixture"); return activity;
    }
    @Test void scheduledStartAndEndEvictAllRegions() {
        var start = activity(0); var end = activity(1); end.setId(8L);
        when(mapper.selectList(any())).thenReturn(List.of(start), List.of(end));
        when(mapper.startIfDue(eq(7L), any())).thenReturn(1);
        when(mapper.finishIfDue(eq(8L), any())).thenReturn(1);
        scheduler.syncActivityStatus();
        verify(cache).evictAfterCommit(7L); verify(cache).evictAfterCommit(8L);
        verify(mapper).startIfDue(eq(7L), any());
        verify(mapper).finishIfDue(eq(8L), any());
        verify(mapper, never()).updateById(any(Activity.class));
    }
    @Test void failedScheduledWritesDoNotEvict() {
        when(mapper.selectList(any())).thenReturn(List.of(activity(0)), List.of());
        scheduler.syncActivityStatus(); verifyNoInteractions(cache);
    }
    @Test void delayedStartEvicts() {
        when(mapper.startIfDue(eq(7L), any())).thenReturn(1);
        handler.start(7L); verify(cache).evictAfterCommit(7L);
        verify(mapper).startIfDue(eq(7L), any());
        verify(mapper, never()).updateById(any(Activity.class));
    }
    @Test void delayedStatisticsEvictsEndAndSignupChanges() {
        when(mapper.selectById(7L)).thenReturn(activity(1));
        when(mapper.finishForStatisticsIfDue(eq(7L), any())).thenReturn(1);
        var signup = new ActivitySignup();
        when(signups.selectList(any())).thenReturn(List.of(signup));
        handler.statistics(7L); verify(cache).evictAfterCommit(7L);
        verify(mapper).finishForStatisticsIfDue(eq(7L), any());
        verify(mapper, never()).updateById(any(Activity.class));
    }
}
