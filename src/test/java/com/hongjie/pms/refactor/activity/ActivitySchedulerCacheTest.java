package com.hongjie.pms.refactor.activity;

import com.hongjie.pms.common.punishment.scheduler.*;
import com.hongjie.pms.modules.activity.cache.ActivityCache;
import com.hongjie.pms.modules.activity.entity.*;
import com.hongjie.pms.modules.activity.mapper.*;
import com.hongjie.pms.modules.message.service.MessageService;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import com.hongjie.pms.common.punishment.util.PunishmentUtil;
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
    @InjectMocks DelayTaskHandler handler;

    Activity activity(int status) {
        var activity = new Activity(); activity.setId(7L); activity.setStatus(status);
        activity.setEndTime(LocalDateTime.now().minusMinutes(2)); return activity;
    }
    @Test void scheduledStartAndEndEvictAllRegions() {
        var start = activity(0); var end = activity(1); end.setId(8L);
        when(mapper.selectList(any())).thenReturn(List.of(start), List.of(end));
        when(mapper.updateById(any(Activity.class))).thenReturn(1);
        scheduler.syncActivityStatus();
        verify(cache).evictAfterCommit(7L); verify(cache).evictAfterCommit(8L);
    }
    @Test void failedScheduledWritesDoNotEvict() {
        when(mapper.selectList(any())).thenReturn(List.of(activity(0)), List.of());
        scheduler.syncActivityStatus(); verifyNoInteractions(cache);
    }
    @Test void delayedStartEvicts() {
        when(mapper.selectById(7L)).thenReturn(activity(0));
        when(mapper.updateById(any(Activity.class))).thenReturn(1);
        handler.handleActivityStart(7L); verify(cache).evictAfterCommit(7L);
    }
    @Test void delayedStatisticsEvictsEndAndSignupChanges() {
        when(mapper.selectById(7L)).thenReturn(activity(1));
        when(mapper.updateById(any(Activity.class))).thenReturn(1);
        var signup = new ActivitySignup();
        when(signups.selectList(any())).thenReturn(List.of(signup));
        when(signups.updateById(signup)).thenReturn(1);
        handler.handleActivityStatistics(7L); verify(cache, times(2)).evictAfterCommit(7L);
    }
}
