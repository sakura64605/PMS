package com.hongjie.pms.modules.scheduling.application;

import com.hongjie.pms.modules.activity.application.ActivityStatusScheduler;
import com.hongjie.pms.modules.community.feed.application.task.InboxCleanupTask;
import com.hongjie.pms.modules.analytics.api.service.StatisticsService;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PmsJobActionsTest {
    StatisticsService statistics = mock(StatisticsService.class);
    RedissonClient redisson = mock(RedissonClient.class);
    RLock lock = mock(RLock.class);
    PmsJobActions actions =
            new PmsJobActions(
                    mock(DueTaskBatch.class),
                    mock(ActivityStatusScheduler.class),
                    mock(InboxCleanupTask.class),
                    statistics,
                    redisson);

    @Test
    void dailyAndCompensationUseSameWatchdogMutex() {
        when(redisson.getLock("pms:scheduled:statistics")).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        actions.dailyStatistics();
        actions.statisticsCompensation();
        verify(redisson, times(2)).getLock("pms:scheduled:statistics");
        verify(statistics).generateDailyStatistics();
        verify(statistics).compensateMissingStatistics();
        verify(lock, times(2)).unlock();
    }

    @Test
    void failedJobReleasesLockAndReportsFailure() {
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        doThrow(new IllegalStateException("injected")).when(statistics).generateDailyStatistics();
        assertThatThrownBy(actions::dailyStatistics).isInstanceOf(IllegalStateException.class);
        verify(lock).unlock();
    }

    @Test
    void busyJobFailsWithoutRunningOrUnlockingOtherOwner() {
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(false);
        assertThatThrownBy(actions::statisticsCompensation)
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(statistics);
        verify(lock, never()).unlock();
    }
}
