package com.hongjie.pms.modules.scheduling.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hongjie.pms.modules.activity.infrastructure.persistence.entity.*;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.*;
import com.hongjie.pms.modules.activity.infrastructure.cache.ActivityCache;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import com.hongjie.pms.modules.identity.user.infrastructure.persistence.mapper.UserMapper;
import com.hongjie.pms.modules.communication.notification.api.service.MessageService;
import com.hongjie.pms.modules.scheduling.application.DelayTaskService;
import com.hongjie.pms.modules.identity.user.domain.policy.PunishmentUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

/** Invoked inside the task transaction, after the activity/user and task rows have been locked. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ScheduledBusinessTasks {
    private final ActivityMapper activities;
    private final ActivitySignupMapper signups;
    private final UserMapper users;
    private final com.hongjie.pms.modules.identity.user.application.UserCache userCache;
    private final ScheduledTaskMapper locks;
    private final ActivityCache cache;
    private final MessageService messages;
    private final PunishmentUtil punishment;
    private final DelayTaskService delays;

    public void start(Long id) {
        if (activities.startIfDue(id, LocalDateTime.now()) == 1) cache.evictAfterCommit(id);
    }

    public void remind(Long id) {
        Activity row = activities.selectById(id);
        LocalDateTime now = LocalDateTime.now();
        if (!visible(row)
                || !Objects.equals(row.getStatus(), 0)
                || !row.getStartTime().isAfter(now)
                || row.getStartTime().isAfter(now.plusMinutes(30))) return;
        int minutes = (int) java.time.Duration.between(now, row.getStartTime()).toMinutes();
        for (var signup :
                signups.selectList(
                        new LambdaQueryWrapper<ActivitySignup>()
                                .eq(ActivitySignup::getActivityId, id)
                                .eq(ActivitySignup::getStatus, 1)))
            messages.sendActivityReminder(signup.getUserId(), row.getTitle(), id, minutes);
    }

    public void statistics(Long id) {
        Activity row = activities.selectById(id);
        LocalDateTime now = LocalDateTime.now();
        if (!visible(row)
                || row.getEndTime().isAfter(now)
                || !Set.of(0, 1, 2).contains(row.getStatus())
                || locks.statisticsAlreadyDone(id) > 0) return;
        if (activities.finishForStatisticsIfDue(id, now) != 1)
            throw new IllegalStateException("Activity statistics transition failed");
        var rows =
                signups.selectList(
                        new LambdaQueryWrapper<ActivitySignup>()
                                .eq(ActivitySignup::getActivityId, id)
                                .ne(ActivitySignup::getStatus, 2)
                                .orderByAsc(ActivitySignup::getUserId)
                                .last("FOR UPDATE"));
        // Transition only newly absent participants. Repeated task records cannot add another
        // penalty.
        for (var signup : rows) {
            if (!Objects.equals(signup.getStatus(), 1) || signup.getCheckInTime() != null) continue;
            if (signups.update(
                            null,
                            new LambdaUpdateWrapper<ActivitySignup>()
                                    .eq(ActivitySignup::getId, signup.getId())
                                    .eq(ActivitySignup::getStatus, 1)
                                    .isNull(ActivitySignup::getCheckInTime)
                                    .set(ActivitySignup::getStatus, 4)
                                    .set(ActivitySignup::getUpdateTime, now))
                    != 1) throw new IllegalStateException("Signup statistics transition failed");
            signup.setStatus(4);
            locks.lockUser(signup.getUserId());
            updateAbsence(signup.getUserId(), now);
        }
        long checked = rows.stream().filter(signup -> signup.getCheckInTime() != null).count();
        long absent = rows.stream().filter(signup -> Objects.equals(signup.getStatus(), 4)).count();
        messages.sendActivityStatisticsNotification(
                row.getUserId(), row.getTitle(), id, rows.size(), checked, absent);
        cache.evictAfterCommit(id);
    }

    private void updateAbsence(Long userId, LocalDateTime now) {
        User user = users.selectById(userId);
        if (user == null) return;
        int total =
                Math.toIntExact(
                        signups.selectCount(
                                new LambdaQueryWrapper<ActivitySignup>()
                                        .eq(ActivitySignup::getUserId, userId)
                                        .ne(ActivitySignup::getStatus, 2)));
        int absent =
                Math.toIntExact(
                        signups.selectCount(
                                new LambdaQueryWrapper<ActivitySignup>()
                                        .eq(ActivitySignup::getUserId, userId)
                                        .eq(ActivitySignup::getStatus, 4)));
        int recentTotal =
                Math.toIntExact(
                        signups.selectCount(
                                new LambdaQueryWrapper<ActivitySignup>()
                                        .eq(ActivitySignup::getUserId, userId)
                                        .ne(ActivitySignup::getStatus, 2)
                                        .ge(ActivitySignup::getCreateTime, now.minusDays(30))));
        int recentAbsent =
                Math.toIntExact(
                        signups.selectCount(
                                new LambdaQueryWrapper<ActivitySignup>()
                                        .eq(ActivitySignup::getUserId, userId)
                                        .eq(ActivitySignup::getStatus, 4)
                                        .ge(ActivitySignup::getCreateTime, now.minusDays(30))));
        var update =
                new LambdaUpdateWrapper<User>()
                        .eq(User::getId, userId)
                        .set(User::getTotalSignups, total)
                        .set(User::getTotalNoShows, absent)
                        .set(User::getRecentNoShows, recentAbsent);
        int days = punishment.calculateRecentPunishmentDays(recentTotal, recentAbsent);
        if (days > 0
                && (user.getPunishmentEndTime() == null
                        || !user.getPunishmentEndTime().isAfter(now))) {
            LocalDateTime until = now.plusDays(days);
            update.set(User::getIsBannedSignup, 1)
                    .set(User::getBanSignupEndTime, until)
                    .set(User::getPunishmentEndTime, until);
            delays.addTask("SIGNUP_PUNISHMENT_END", userId, until);
            messages.sendPunishmentStartNotification(userId, "近期活动爽约率过高，禁止报名" + days + "天");
        }
        if (users.update(null, update) != 1)
            throw new IllegalStateException("Absence summary update failed");
        userCache.afterCommit(userId);
    }

    public void signupExpiry(Long id) {
        User user = users.selectById(id);
        if (user == null) return;
        LocalDateTime until =
                com.hongjie.pms.modules.identity.user.application.UserRestrictions.signupUntil(
                        user);
        if (until != null && until.isAfter(LocalDateTime.now())) return;
        users.update(
                null,
                new LambdaUpdateWrapper<User>()
                        .eq(User::getId, id)
                        .set(User::getIsBannedSignup, 0)
                        .set(User::getBanSignupEndTime, null)
                        .set(User::getPunishmentEndTime, null));
        userCache.afterCommit(id);
    }

    public void muteExpiry(Long id) {
        User user = users.selectById(id);
        if (user == null
                || user.getMuteEndTime() == null
                || user.getMuteEndTime().isAfter(LocalDateTime.now())) return;
        users.update(
                null,
                new LambdaUpdateWrapper<User>()
                        .eq(User::getId, id)
                        .set(User::getIsMuted, 0)
                        .set(User::getMuteEndTime, null));
        userCache.afterCommit(id);
    }

    public void banExpiry(Long id) {
        User user = users.selectById(id);
        if (user == null) return;
        // The legacy schema stores timed account bans in delay_task rather than a user end-time
        // column.
        LocalDateTime latest = locks.latestBanExpiry(id);
        if (latest != null && latest.isAfter(LocalDateTime.now())) return;
        users.update(
                null, new LambdaUpdateWrapper<User>().eq(User::getId, id).set(User::getStatus, 1));
        userCache.afterCommit(id);
    }

    private boolean visible(Activity row) {
        return row != null
                && Objects.equals(row.getDeleted(), 0)
                && Objects.equals(row.getAuditStatus(), 1)
                && row.getStartTime() != null
                && row.getEndTime() != null;
    }
}
