package com.hongjie.pms.common.punishment.util;

import com.hongjie.pms.common.punishment.config.PunishmentConfig;
import com.hongjie.pms.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 惩罚工具类
 * 用于计算违规惩罚天数、检查惩罚期等
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PunishmentUtil {

    private final PunishmentConfig punishmentConfig;

    /**
     * 计算惩罚天数
     * @param totalCount 总次数（报名/参与等）
     * @param violationCount 违规次数（爽约/未支付等）
     * @return 惩罚天数，0表示不惩罚
     */
    public int calculatePunishmentDays(int totalCount, int violationCount) {
        // 1. 次数太少，不惩罚
        if (totalCount < punishmentConfig.getMinSignups()) {
            return 0;
        }

        // 2. 计算违规率
        double violationRate = (double) violationCount / totalCount;

        // 3. 低于阈值，不惩罚
        if (violationRate <= punishmentConfig.getNoShowRateThreshold()) {
            return 0;
        }

        // 4. 根据规则匹配
        for (PunishmentConfig.Rule rule : punishmentConfig.getRules()) {
            if (violationRate >= rule.getMinRate() && violationRate < rule.getMaxRate()) {
                log.debug("违规率{:.1%}，惩罚{}天", violationRate, rule.getDays());
                return rule.getDays();
            }
        }

        return 0;
    }

    /**
     * 计算惩罚天数（基于用户对象）
     */
    public int calculatePunishmentDays(User user) {
        // 使用总报名数和总爽约数进行惩罚计算
        int totalSignups = user.getTotalSignups() != null ? user.getTotalSignups() : 0;
        int totalNoShows = user.getTotalNoShows() != null ? user.getTotalNoShows() : 0;
        return calculatePunishmentDays(totalSignups, totalNoShows);
    }

    /**
     * 计算惩罚天数（基于最近数据）
     */
    public int calculateRecentPunishmentDays(int recentSignups, int recentNoShows) {
        return calculatePunishmentDays(recentSignups, recentNoShows);
    }

    /**
     * 检查用户是否在惩罚期
     */
    public boolean isInPunishment(LocalDateTime punishmentEndTime) {
        if (punishmentEndTime == null) {
            return false;
        }
        return punishmentEndTime.isAfter(LocalDateTime.now());
    }

    /**
     * 检查用户是否在惩罚期（基于用户对象）
     */
    public boolean isInPunishment(User user) {
        return isInPunishment(user.getPunishmentEndTime());
    }

    /**
     * 获取惩罚剩余天数
     */
    public long getRemainingPunishmentDays(LocalDateTime punishmentEndTime) {
        if (!isInPunishment(punishmentEndTime)) {
            return 0;
        }
        return java.time.Duration.between(LocalDateTime.now(), punishmentEndTime).toDays() + 1;
    }

    /**
     * 获取惩罚剩余天数（基于用户对象）
     */
    public long getRemainingPunishmentDays(User user) {
        return getRemainingPunishmentDays(user.getPunishmentEndTime());
    }

    /**
     * 创建惩罚结束时间
     */
    public LocalDateTime createPunishmentEndTime(int days) {
        return days > 0 ? LocalDateTime.now().plusDays(days) : null;
    }

    /**
     * 格式化惩罚提示信息
     */
    public String getPunishmentMessage(User user) {
        if (!isInPunishment(user)) {
            return null;
        }
        long days = getRemainingPunishmentDays(user);
        return String.format("您因爽约率过高，已被禁止报名 %d 天", days);
    }
}