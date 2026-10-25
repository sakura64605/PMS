package com.hongjie.pms.refactor.activity;

import com.hongjie.pms.common.punishment.scheduler.*;
import com.hongjie.pms.refactor.scheduling.DelayTaskService;
import com.hongjie.pms.modules.activity.cache.ActivityCache;
import com.hongjie.pms.modules.activity.entity.*;
import com.hongjie.pms.modules.activity.mapper.*;
import com.hongjie.pms.refactor.message.api.MessageService;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.refactor.user.policy.PunishmentUtil;
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
    @Mock com.hongjie.pms.refactor.scheduling.ScheduledTaskMapper locks;
    @Mock com.hongjie.pms.refactor.user.UserCache userCache;
    @InjectMocks com.hongjie.pms.refactor.scheduling.ScheduledBusinessTasks handler;

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
