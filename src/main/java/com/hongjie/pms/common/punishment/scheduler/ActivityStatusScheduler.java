package com.hongjie.pms.common.punishment.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.cache.toolkit.CacheUtil;
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
public class ActivityStatusScheduler {

    private final ActivityMapper activityMapper;
    private final DistributedCache distributedCache;

    @Scheduled(fixedDelay = 60000)
    public void syncActivityStatus() {
        LocalDateTime now = LocalDateTime.now();

        List<Activity> starting = activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getStatus, 0)
                .eq(Activity::getDeleted, 0)
                .le(Activity::getStartTime, now));
        for (Activity activity : starting) {
            try {
                activity.setStatus(1);
                activity.setUpdateTime(now);
                activityMapper.updateById(activity);
                distributedCache.delete(CacheUtil.buildKey("activity", String.valueOf(activity.getId())));
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
                activity.setStatus(2);
                activity.setUpdateTime(now);
                activityMapper.updateById(activity);
                distributedCache.delete(CacheUtil.buildKey("activity", String.valueOf(activity.getId())));
                log.info("定时任务：活动已变更为已结束: id={}", activity.getId());
            } catch (Exception e) {
                log.error("定时任务：活动结束失败: id={}", activity.getId(), e);
            }
        }
    }
}