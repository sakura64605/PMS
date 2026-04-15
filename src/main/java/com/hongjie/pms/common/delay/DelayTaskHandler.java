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

import java.time.Duration;
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
     * 处理延时任务
     */
    public void handle(DelayTask task) {
        switch (task.getType()) {
            case "ACTIVITY_REMIND":
                handleActivityRemind(task.getBusinessId());
                break;
            case "ACTIVITY_STATISTICS":
                handleActivityStatistics(task.getBusinessId());
                break;
            default:
                log.warn("未知的任务类型: {}", task.getType());
        }
    }
    
    /**
     * 活动开始前提醒
     */
    private void handleActivityRemind(Long activityId) {
        log.info("执行活动提醒: activityId={}", activityId);
        
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null || activity.getStatus() != 0) {
            log.warn("活动不存在或已结束: activityId={}", activityId);
            return;
        }
        
        // 查询所有报名用户
        List<ActivitySignup> signups = activitySignupMapper.selectList(
            new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getStatus, 1)
        );
        
        // 计算剩余分钟数
        long minutes = Duration.between(LocalDateTime.now(), activity.getStartTime()).toMinutes();
        
        // 发送提醒
        for (ActivitySignup signup : signups) {
            messageService.sendActivityReminder(
                signup.getUserId(),
                activity.getTitle(),
                activityId,
                (int) minutes
            );
        }
        
        log.info("活动提醒完成: activityId={}, 通知人数={}", activityId, signups.size());
    }
    
    /**
     * 活动结束后统计
     */
    private void handleActivityStatistics(Long activityId) {
        log.info("执行活动统计: activityId={}", activityId);
        
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            return;
        }
        
        // 查询所有报名用户
        List<ActivitySignup> signups = activitySignupMapper.selectList(
            new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, activityId)
        );
        
        long signedCount = signups.stream()
                .filter(s -> s.getCheckInTime() != null)
                .count();
        
        long noShowCount = signups.size() - signedCount;
        
        log.info("活动统计: activityId={}, 报名={}, 签到={}, 爽约={}", 
            activityId, signups.size(), signedCount, noShowCount);
        
        // 发送统计报告
        messageService.sendActivityStatisticsNotification(
            activity.getUserId(),
            activity.getTitle(),
            activityId,
            signups.size(),
            signedCount,
            noShowCount
        );
    }
}