package com.hongjie.pms.modules.message.mq;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.common.mq.DelayMessageDto;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.entity.ActivitySignup;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.activity.mapper.ActivitySignupMapper;
import com.hongjie.pms.modules.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "delay-task-topic",
        selectorExpression = "delay",
        consumerGroup = "delay-task-consumer-group"
)
public class DelayTaskConsumer implements RocketMQListener<String> {
    
    private final ActivityMapper activityMapper;
    private final ActivitySignupMapper activitySignupMapper;
    private final MessageService messageService;
    
    @Override
    public void onMessage(String message) {
        try {
            DelayMessageDto dto = JSON.parseObject(message, DelayMessageDto.class);
            log.info("收到延迟任务: type={}, businessId={}", dto.getType(), dto.getBusinessId());
            
            switch (dto.getType()) {
                case "ACTIVITY_REMIND":
                    handleActivityRemind(dto.getBusinessId());
                    break;
                case "ACTIVITY_STATISTICS":
                    handleActivityStatistics(dto.getBusinessId());
                    break;
                default:
                    log.warn("未知的延迟任务类型: {}", dto.getType());
            }
        } catch (Exception e) {
            log.error("处理延迟任务失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 活动开始前提醒
     */
    private void handleActivityRemind(Long activityId) {
        log.info("执行活动提醒: activityId={}", activityId);
        
        // 查询活动信息
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
        
        // 发送提醒消息
        for (ActivitySignup signup : signups) {
            messageService.sendActivityReminder(
                signup.getUserId(),
                activity.getTitle(),
                activityId,
                30  // 30分钟后开始
            );
        }
        
        log.info("活动提醒完成: activityId={}, 通知人数={}", activityId, signups.size());
    }
    
    /**
     * 活动结束后统计
     */
    private void handleActivityStatistics(Long activityId) {
        log.info("执行活动统计: activityId={}", activityId);
        
        // 查询活动信息
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            return;
        }
        
        // 查询所有报名用户
        List<ActivitySignup> signups = activitySignupMapper.selectList(
            new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, activityId)
        );
        
        // 统计签到人数
        Long signedCount = signups.stream()
                .filter(s -> s.getCheckInTime() != null)
                .count();
        
        Long noShowCount = signups.size() - signedCount;
        
        log.info("活动统计: activityId={}, 报名人数={}, 签到人数={}, 爽约人数={}", 
            activityId, signups.size(), signedCount, noShowCount);
        
        // 更新活动的统计信息
        activity.setCommentCount(signedCount.intValue());
        activityMapper.updateById(activity);
        
        // 发送统计报告给活动创建者
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