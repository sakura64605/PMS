package com.hongjie.pms.refactor.scheduling;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pms.scheduling.mode", havingValue = "local", matchIfMissing = true)
public class LocalJobScheduler {
    private final PmsJobActions jobs;
    @Scheduled(fixedDelayString = "${pms.scheduling.local-delay-ms:60000}") public void due() { jobs.dueTasks(); }
    @Scheduled(fixedDelayString = "${pms.scheduling.local-delay-ms:60000}") public void activity() { jobs.activityStatus(); }
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Shanghai") public void inbox() { jobs.inboxCleanup(); }
    @Scheduled(cron = "0 0 1 * * ?", zone = "Asia/Shanghai") public void daily() { jobs.dailyStatistics(); }
    @Scheduled(cron = "0 0 3,6,9,12,15,18,21 * * ?", zone = "Asia/Shanghai") public void compensate() { jobs.statisticsCompensation(); }
}
