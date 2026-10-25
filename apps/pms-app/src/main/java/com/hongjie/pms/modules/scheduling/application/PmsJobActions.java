package com.hongjie.pms.modules.scheduling.application;

import com.hongjie.pms.modules.activity.application.ActivityStatusScheduler;
import com.hongjie.pms.modules.community.feed.application.task.InboxCleanupTask;
import com.hongjie.pms.modules.analytics.api.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

/** Local and XXL modes invoke the same actions. The watchdog lock has no short fixed lease. */
@Service
@RequiredArgsConstructor
public class PmsJobActions {
    private final DueTaskBatch due;
    private final ActivityStatusScheduler activities;
    private final InboxCleanupTask inbox;
    private final StatisticsService statistics;
    private final RedissonClient redisson;

    public void dueTasks() { due.execute(); }
    public void activityStatus() { guarded("activity-status", activities::syncActivityStatus); }
    public void inboxCleanup() { guarded("inbox-cleanup", inbox::cleanupOldReadMessages); }
    public void dailyStatistics() { guarded("statistics", statistics::generateDailyStatistics); }
    public void statisticsCompensation() { guarded("statistics", statistics::compensateMissingStatistics); }
    private void guarded(String name, Runnable action) {
        var lock = redisson.getLock("pms:scheduled:" + name);
        if (!lock.tryLock()) throw new IllegalStateException("Scheduled job already running: " + name);
        try { action.run(); }
        finally { if (lock.isHeldByCurrentThread()) lock.unlock(); }
    }
}
