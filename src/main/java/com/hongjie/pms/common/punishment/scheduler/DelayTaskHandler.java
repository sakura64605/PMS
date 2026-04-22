package com.hongjie.pms.common.punishment.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.cache.toolkit.CacheUtil;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.punishment.util.PunishmentUtil;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.entity.ActivitySignup;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.activity.mapper.ActivitySignupMapper;
import com.hongjie.pms.modules.message.service.MessageService;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
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
    private final UserMapper userMapper;
    private final PunishmentUtil punishmentUtil;
    private final DelayTaskService delayTaskService;
    private final DistributedCache distributedCache;

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

        LocalDateTime now = LocalDateTime.now();
        if (activity.getEndTime().isAfter(now)) {
            log.warn("活动未结束，跳过统计: activityId={}", activityId);
            return;
        } else {
            activity.setStatus(2);
            activityMapper.updateById(activity);
            String cacheKey = CacheUtil.buildKey("activity", String.valueOf(activityId));
            distributedCache.delete(cacheKey);
        }

        // 查询所有报名用户
        List<ActivitySignup> signups = activitySignupMapper.selectList(
                new LambdaQueryWrapper<ActivitySignup>()
                        .eq(ActivitySignup::getActivityId, activityId)
        );

        long totalSignups = signups.size();
        long signedCount = 0;
        long noShowCount = 0;

        for (ActivitySignup signup : signups) {
            // 如果活动已结束且未签到，标记为爽约
            if (signup.getCheckInTime() == null &&
                    (activity.getStatus() == 2 || activity.getEndTime().isBefore(LocalDateTime.now()))) {
                signup.setStatus(4); // 4-爽约
                activitySignupMapper.updateById(signup);
                noShowCount++;
            } else if (signup.getCheckInTime() != null) {
                signedCount++;
            } else {
                // 已报名未结束，不算爽约
            }
        }

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

        // 更新用户爽约数据并处理惩罚（只处理爽约的用户）
        updateUserNoShowData(signups);
    }

    /**
     * 用户报名惩罚结束
     */
    public void handleSignupEnd(Long userId) {
        log.info("执行报名用户惩罚结束: userId={}", userId);
        User user = userMapper.selectById(userId);
        if (punishmentUtil.isInPunishment(user)){
            throw new BusinessException("惩罚时间未结束");
        }
        user.setPunishmentEndTime(null);
        user.setIsBannedSignup(0);
        userMapper.updateById(user);
    }

    /**
     * 用户禁言惩罚结束
     */
    public void handleMutedEnd(Long userId) {
        log.info("执行用户禁言惩罚结束: userId={}", userId);
        User user = userMapper.selectById(userId);

        user.setIsMuted(0);
        userMapper.updateById(user);
    }

    /**
     * 计算用户最近30天的爽约数
     */
    private int calculateRecentNoShows(Long userId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        List<ActivitySignup> recentSignups = activitySignupMapper.selectList(
                new LambdaQueryWrapper<ActivitySignup>()
                        .eq(ActivitySignup::getUserId, userId)
                        .ge(ActivitySignup::getCreateTime, thirtyDaysAgo)
        );

        long noShowCount = recentSignups.stream()
                .filter(signup -> signup.getStatus() == 4) // 爽约状态
                .count();

        return (int) noShowCount;
    }

    /**
     * 计算用户最近30天的报名数
     */
    private int calculateRecentSignups(Long userId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        long signupCount = activitySignupMapper.selectCount(
                new LambdaQueryWrapper<ActivitySignup>()
                        .eq(ActivitySignup::getUserId, userId)
                        .ge(ActivitySignup::getCreateTime, thirtyDaysAgo)
                        .ne(ActivitySignup::getStatus, 2) // 排除已取消的报名
        );

        return (int) signupCount;
    }

    /**
     * 更新用户爽约数据并处理惩罚
     */
    private void updateUserNoShowData(List<ActivitySignup> signups) {
        for (ActivitySignup signup : signups) {
            try {
                // 只处理爽约的记录
                if (signup.getStatus() != 4) {
                    continue;
                }

                Long userId = signup.getUserId();
                User user = userMapper.selectById(userId);
                if (user == null) {
                    log.warn("用户不存在: userId={}", userId);
                    continue;
                }

                // 更新总报名数
                user.setTotalSignups(user.getTotalSignups() != null ? user.getTotalSignups() + 1 : 1);

                // 更新总爽约数
                user.setTotalNoShows(user.getTotalNoShows() != null ? user.getTotalNoShows() + 1 : 1);

                // 计算最近30天的爽约数
                int recentNoShows = calculateRecentNoShows(userId);
                user.setRecentNoShows(recentNoShows);

                // 计算最近30天的报名数
                int recentSignups = calculateRecentSignups(userId);

                // 计算惩罚天数
                int punishmentDays = punishmentUtil.calculateRecentPunishmentDays(recentSignups, recentNoShows);

                if (punishmentDays > 0 && !isUserInPunishment(user)) {
                    // 设置惩罚
                    user.setPunishmentEndTime(punishmentUtil.createPunishmentEndTime(punishmentDays));
                    user.setIsBannedSignup(1);
                    user.setBanSignupEndTime(user.getPunishmentEndTime());

                    log.info("用户爽约率过高，已禁止报名: userId={}, 最近报名={}, 最近爽约={}, 惩罚天数={}",
                            userId, recentSignups, recentNoShows, punishmentDays);
                    delayTaskService.addTask("SIGNUP_PUNISHMENT_END", userId, user.getPunishmentEndTime());
                    // 发送惩罚通知
                    try {
                        String message = String.format("您最近30天内爽约%d次（共报名%d次），爽约率过高，已被禁止报名%d天。",
                                recentNoShows, recentSignups, punishmentDays);
                        messageService.sendPunishmentStartNotification(userId, message);
                    } catch (Exception e) {
                        log.error("发送惩罚通知失败: userId={}", userId, e);
                    }
                }

                // 更新用户信息
                userMapper.updateById(user);

            } catch (Exception e) {
                log.error("更新用户爽约数据失败: signupId={}", signup.getId(), e);
            }
        }
    }

    /**
     * 检查用户是否已在惩罚期
     */
    private boolean isUserInPunishment(User user) {
        if (user.getPunishmentEndTime() == null) {
            return false;
        }
        return user.getPunishmentEndTime().isAfter(LocalDateTime.now());
    }

    public void handleActivityStart(Long businessId) {
        log.info("执行活动开始: activityId={}", businessId);
        Activity activity = activityMapper.selectById(businessId);
        activity.setStatus(1);
        activityMapper.updateById(activity);
    }
}