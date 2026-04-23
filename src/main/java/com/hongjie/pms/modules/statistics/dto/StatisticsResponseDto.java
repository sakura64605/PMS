package com.hongjie.pms.modules.statistics.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class StatisticsResponseDto {

    // 日报
    private DailyStatisticsDto dailyStats;
    
    // 周报
    private WeeklyStatisticsDto weeklyStats;
    
    // 月报
    private MonthlyStatisticsDto monthlyStats;
    
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
    }
    
    @Data
    @Builder
    public static class WeeklyStatisticsDto {
        private String weekRange;           // "2024-01-01 ~ 2024-01-07"
        private Integer avgDailyActiveUsers; // 日均活跃用户
        private Integer totalNewUsers;
        private Integer totalNewPosts;
        private Integer totalNewComments;
        private Integer totalNewLikes;
        private Integer weekOverWeekGrowth;  // 环比增长百分比
    }
    
    @Data
    @Builder
    public static class MonthlyStatisticsDto {
        private String month;                // "2024-01"
        private Integer totalActiveUsers;
        private Integer totalNewUsers;
        private Integer totalNewPosts;
        private Integer totalNewComments;
        private Integer monthOverMonthGrowth; // 环比增长百分比
    }
    
    @Data
    @Builder
    public static class TrendDataDto {
        private List<String> dates;          // 日期列表
        private List<Integer> dauList;       // DAU趋势
        private List<Integer> newUserList;   // 新增用户趋势
        private List<Integer> newPostList;   // 新增帖子趋势
        private List<Integer> newCommentList;// 新增评论趋势
        private List<Integer> newLikeList;   // 新增点赞趋势
    }
}