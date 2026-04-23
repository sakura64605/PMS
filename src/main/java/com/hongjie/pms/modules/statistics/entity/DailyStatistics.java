package com.hongjie.pms.modules.statistics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("daily_statistics")
public class DailyStatistics {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate statDate;           // 统计日期

    // ========== 用户统计 ==========
    private Integer newUserCount;         // 新增用户数
    private Integer totalUserCount;       // 累计用户数
    private Integer activeUserCount;      // 活跃用户数（当日有操作）
    private Integer dau;                  // 日活跃用户
    private Integer wau;                  // 周活跃用户
    private Integer mau;                  // 月活跃用户

    // ========== 内容统计 ==========
    private Integer newPetPostCount;      // 新增宠物帖子数
    private Integer totalPetPostCount;    // 累计宠物帖子数
    private Integer newActivityCount;     // 新增活动数
    private Integer totalActivityCount;   // 累计活动数
    private Integer newDailyPostCount;    // 新增日常动态数
    private Integer totalDailyPostCount;  // 累计日常动态数
    private Integer newCommentCount;      // 新增评论数
    private Integer totalCommentCount;    // 累计评论数

    // ========== 互动统计 ==========
    private Integer newLikeCount;         // 新增点赞数
    private Integer totalLikeCount;       // 累计点赞数
    private Integer newFollowCount;       // 新增关注数
    private Integer totalFollowCount;     // 累计关注数
    private Integer newFavoriteCount;     // 新增收藏数
    private Integer totalFavoriteCount;   // 累计收藏数
    private Integer newShareCount;        // 新增分享数
    private Integer totalShareCount;      // 累计分享数

    // ========== 活动统计 ==========
    private Integer newSignupCount;       // 新增报名数
    private Integer totalSignupCount;     // 累计报名数
    private Integer newCheckinCount;      // 新增签到数
    private Integer totalCheckinCount;    // 累计签到数

    // ========== 审核统计 ==========
    private Integer pendingAuditCount;    // 待审核数量
    private Integer approvedCount;        // 审核通过数
    private Integer rejectedCount;        // 审核拒绝数

    // ========== 举报统计 ==========
    private Integer newReportCount;       // 新增举报数
    private Integer pendingReportCount;   // 待处理举报数
    private Integer handledReportCount;   // 已处理举报数

    // ========== 私信统计 ==========
    private Integer newPrivateMessageCount;   // 新增私信数
    private Integer totalPrivateMessageCount; // 累计私信数

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}