package com.hongjie.pms.common.punishment.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.modules.activity.cache.ActivityCache;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("legacy-reference")
public class ActivityStatusScheduler {

    private final ActivityMapper activityMapper;
    private final ActivityCache activityCache;

    public void syncActivityStatus() {
        LocalDateTime now = LocalDateTime.now();

        List<Activity> starting = activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getStatus, 0)
                .eq(Activity::getAuditStatus, 1)
                .eq(Activity::getDeleted, 0)
                .le(Activity::getStartTime, now));
        for (Activity activity : starting) {
            try {
                if (activityMapper.startIfDue(activity.getId(), now) > 0) {
                    activityCache.evictAfterCommit(activity.getId());
                }
                log.info("定时任务：活动已变更为进行中: id={}", activity.getId());
            } catch (Exception e) {
                log.error("定时任务：活动开始失败: id={}", activity.getId(), e);
            }
        }

        List<Activity> ending = activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getStatus, 1)
                .eq(Activity::getDeleted, 0)
                .le(Activity::getEndTime, now));
        for (Activity activity : ending) {
            try {
                if (activityMapper.finishIfDue(activity.getId(), now) > 0) {
                    activityCache.evictAfterCommit(activity.getId());
                }
                log.info("定时任务：活动已变更为已结束: id={}", activity.getId());
            } catch (Exception e) {
                log.error("定时任务：活动结束失败: id={}", activity.getId(), e);
            }
        }
    }
}
