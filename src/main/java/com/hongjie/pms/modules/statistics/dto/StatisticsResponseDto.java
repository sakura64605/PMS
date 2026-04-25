package com.hongjie.pms.modules.statistics.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class StatisticsResponseDto {

    // 日报
    private DailyStatisticsDto dailyStats;

    // 周报
    private WeeklyStatisticsDto weeklyStats;

    // 月报
    private MonthlyStatisticsDto monthlyStats;

    // 年报
    private YearlyStatisticsDto yearlyStats;

    // 趋势数据
    private TrendDataDto trendData;

    @Data
    @Builder
    public static class DailyStatisticsDto {
        private LocalDate statDate;
        private Integer newUserCount;
        private Integer activeUserCount;
        private Integer dau;
        private Integer newPetPostCount;
        private Integer newActivityCount;
        private Integer newDailyPostCount;
        private Integer newCommentCount;
        private Integer newLikeCount;
        private Integer newFollowCount;
        private Integer newSignupCount;
        private Integer newReportCount;
        private Integer pendingAuditCount;

        // 趋势数据
        private TrendDataDto trendData;
    }

    @Data
    @Builder
    public static class WeeklyStatisticsDto {
        private String weekRange;
        private Integer avgDailyActiveUsers;
        private Integer totalNewUsers;
        private Integer totalNewPosts;
        private Integer totalNewComments;
        private Integer totalNewLikes;
        private Integer weekOverWeekGrowth;

        // 趋势数据
        private TrendDataDto trendData;
    }

    @Data
    @Builder
    public static class MonthlyStatisticsDto {
        private String month;
        private Integer totalActiveUsers;
        private Integer totalNewUsers;
        private Integer totalNewPosts;
        private Integer totalNewComments;
        private Integer monthOverMonthGrowth;
        private Integer avgDailyActiveUsers;

        // 趋势数据
        private TrendDataDto trendData;
    }

    @Data
    @Builder
    public static class YearlyStatisticsDto {
        private Integer year;
        private Integer totalNewUsers;
        private Integer totalNewPosts;
        private Integer totalActiveUsers;
        private Integer yearOverYearGrowth;
        private Integer avgMonthlyActiveUsers;

        // 趋势数据
        private TrendDataDto trendData;
    }

    @Data
    @Builder
    public static class TrendDataDto {
        private List<String> dates;
        private List<Integer> dauList;
        private List<Integer> newUserList;
        private List<Integer> newPostList;
        private List<Integer> newCommentList;
        private List<Integer> newLikeList;
    }

    // StatisticsResponseDto.java - 新增

    @Data
    @Builder
    public static class RangeStatisticsDto {
        // ===== 区间汇总数据 =====
        private LocalDate startDate;           // 开始日期
        private LocalDate endDate;             // 结束日期

        // 用户统计
        private Integer totalNewUsers;         // 区间新增用户
        private Integer avgActiveUsers;        // 区间日均活跃用户
        private Integer maxDau;                // 区间最高DAU
        private Integer minDau;                // 区间最低DAU

        // 内容统计
        private Integer totalNewPosts;         // 区间新增帖子
        private Integer totalNewComments;      // 区间新增评论
        private Integer totalNewLikes;         // 区间新增点赞

        // ===== 趋势数据（折线图用）=====
        private TrendDataDto trendData;
    }
}