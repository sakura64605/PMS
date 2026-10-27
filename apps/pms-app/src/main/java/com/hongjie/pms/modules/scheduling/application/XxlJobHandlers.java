package com.hongjie.pms.modules.scheduling.application;

import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.context.XxlJobHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pms.scheduling.mode", havingValue = "xxl")
public class XxlJobHandlers {
    private final PmsJobActions jobs;

    @XxlJob("pmsDueTasks")
    public void due() {
        run("到期业务任务", jobs::dueTasks);
    }

    @XxlJob("pmsActivityStatus")
    public void activity() {
        run("活动状态", jobs::activityStatus);
    }

    @XxlJob("pmsInboxCleanup")
    public void inbox() {
        run("收件箱清理", jobs::inboxCleanup);
    }

    @XxlJob("pmsDailyStatistics")
    public void daily() {
        run("每日统计", jobs::dailyStatistics);
    }

    @XxlJob("pmsStatisticsCompensation")
    public void compensate() {
        run("统计补偿", jobs::statisticsCompensation);
    }

    private void run(String name, Runnable action) {
        XxlJobHelper.log("开始: " + name);
        action.run(); // Propagate failures so XXL records failures and applies its retry policy.
        XxlJobHelper.log("完成: " + name);
    }
}
