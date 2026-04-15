package com.hongjie.pms.common.delay;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.entity.ActivitySignup;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.activity.mapper.ActivitySignupMapper;
import com.hongjie.pms.modules.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DelayTaskHandler {

    private final ActivityMapper activityMapper;
    private final ActivitySignupMapper activitySignupMapper;
    private final MessageService messageService;

    /**
     * 处理活动提醒
     */
    public void handleActivityRemind(Long activityId) {
        log.info("执行活动提醒: activityId={}", activityId);

        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            log.warn("活动不存在: activityId={}", activityId);
            return;
        }

        if (activity.getStatus() != 0) {
            log.warn("活动状态不是进行中，不发送提醒: activityId={}, status={}", activityId, activity.getStatus());
            return;
        }

        // 查询所有报名用户
        List<ActivitySignup> signups = activitySignupMapper.selectList(
                new LambdaQueryWrapper<ActivitySignup>()
                        .eq(ActivitySignup::getActivityId, activityId)
                        .eq(ActivitySignup::getStatus, 1)
        );

        if (signups.isEmpty()) {
            log.info("活动无报名用户，跳过提醒: activityId={}", activityId);
            return;
        }

        // 计算剩余分钟数
        long minutes = java.time.Duration.between(LocalDateTime.now(), activity.getStartTime()).toMinutes();

        // 发送提醒
        for (ActivitySignup signup : signups) {
            try {
                messageService.sendActivityReminder(
                        signup.getUserId(),
                        activity.getTitle(),
                        activityId,
                        (int) minutes
                );
            } catch (Exception e) {
                log.error("发送活动提醒失败: userId={}, activityId={}", signup.getUserId(), activityId, e);
            }
        }

        log.info("活动提醒完成: activityId={}, 通知人数={}", activityId, signups.size());
    }

    /**
     * 处理活动统计
     */
    public void handleActivityStatistics(Long activityId) {
        log.info("执行活动统计: activityId={}", activityId);

        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            log.warn("活动不存在: activityId={}", activityId);
            return;
        }

        // 查询所有报名用户
        List<ActivitySignup> signups = activitySignupMapper.selectList(
                new LambdaQueryWrapper<ActivitySignup>()
                        .eq(ActivitySignup::getActivityId, activityId)
        );

        long totalSignups = signups.size();
        long signedCount = signups.stream()
                .filter(s -> s.getCheckInTime() != null)
                .count();
        long noShowCount = totalSignups - signedCount;

        log.info("活动统计: activityId={}, 报名={}, 签到={}, 爽约={}",
                activityId, totalSignups, signedCount, noShowCount);

        // 发送统计报告给活动发布者
        try {
            messageService.sendActivityStatisticsNotification(
                    activity.getUserId(),
                    activity.getTitle(),
                    activityId,
                    totalSignups,
                    signedCount,
                    noShowCount
            );
        } catch (Exception e) {
            log.error("发送活动统计报告失败: activityId={}", activityId, e);
        }
    }
}