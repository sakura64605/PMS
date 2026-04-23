package com.hongjie.pms.modules.statistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.common.annotation.RedisRateLimit;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.cache.DistributedCache;
import com.hongjie.pms.common.cache.toolkit.CacheUtil;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.activity.entity.Activity;
import com.hongjie.pms.modules.activity.entity.ActivitySignup;
import com.hongjie.pms.modules.activity.mapper.ActivityMapper;
import com.hongjie.pms.modules.activity.mapper.ActivitySignupMapper;
import com.hongjie.pms.modules.audit.entity.AuditRecord;
import com.hongjie.pms.modules.audit.mapper.AuditRecordMapper;
import com.hongjie.pms.modules.comment.entity.Comment;
import com.hongjie.pms.modules.comment.mapper.CommentMapper;
import com.hongjie.pms.modules.daily.entity.DailyPost;
import com.hongjie.pms.modules.daily.mapper.DailyPostMapper;
import com.hongjie.pms.modules.following.entity.Follow;
import com.hongjie.pms.modules.following.mapper.FollowMapper;
import com.hongjie.pms.modules.like.entity.LikeRecord;
import com.hongjie.pms.modules.like.mapper.LikeRecordMapper;
import com.hongjie.pms.modules.petpost.entity.FavoriteRecord;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.FavoriteRecordMapper;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.privateMessage.entity.PrivateMessage;
import com.hongjie.pms.modules.privateMessage.mapper.PrivateMessageMapper;
import com.hongjie.pms.modules.report.entity.ReportRecord;
import com.hongjie.pms.modules.report.enums.ReportStatus;
import com.hongjie.pms.modules.report.mapper.ReportRecordMapper;
import com.hongjie.pms.modules.statistics.dto.StatisticsQueryDto;
import com.hongjie.pms.modules.statistics.dto.StatisticsResponseDto;
import com.hongjie.pms.modules.statistics.entity.DailyStatistics;
import com.hongjie.pms.modules.statistics.mapper.DailyStatisticsMapper;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final DailyStatisticsMapper statisticsMapper;
    private final UserMapper userMapper;
    private final PetPostMapper petPostMapper;
    private final ActivityMapper activityMapper;
    private final DailyPostMapper dailyPostMapper;
    private final CommentMapper commentMapper;
    private final LikeRecordMapper likeRecordMapper;
    private final FollowMapper followMapper;
    private final FavoriteRecordMapper favoriteRecordMapper;
    private final ActivitySignupMapper activitySignupMapper;
    private final AuditRecordMapper auditRecordMapper;
    private final ReportRecordMapper reportRecordMapper;
    private final PrivateMessageMapper privateMessageMapper;
    private final DistributedCache distributedCache;

    /**
     * 每天凌晨1点执行统计任务
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void generateDailyStatistics() {
        LocalDate statDate = LocalDate.now().minusDays(1);
        log.info("开始生成 {} 的数据统计", statDate);
        
        long startTime = System.currentTimeMillis();

        try {
            // 检查是否已存在
            DailyStatistics existing = statisticsMapper.selectByDate(statDate);
            if (existing != null) {
                log.info("{} 的数据统计已存在，跳过生成", statDate);
                return;
            }

            DailyStatistics stats = new DailyStatistics();
            stats.setStatDate(statDate);

            // 统计用户数据
            fillUserStatistics(stats, statDate);
            
            // 统计内容数据
            fillContentStatistics(stats, statDate);
            
            // 统计互动数据
            fillInteractionStatistics(stats, statDate);
            
            // 统计活动数据
            fillActivityStatistics(stats, statDate);
            
            // 统计审核数据
            fillAuditStatistics(stats, statDate);
            
            // 统计举报数据
            fillReportStatistics(stats, statDate);
            
            // 统计私信数据
            fillMessageStatistics(stats, statDate);

            statisticsMapper.insert(stats);
            
            // 清理统计缓存
            clearStatisticsCache();

            long endTime = System.currentTimeMillis();
            log.info("{} 的数据统计生成完成，耗时 {} ms", statDate, endTime - startTime);

        } catch (Exception e) {
            log.error("生成统计数据失败: {}", statDate, e);
        }
    }

    /**
     * 补录指定日期的统计数据
     */
    @Transactional
    public void regenerateStatistics(LocalDate date) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        
        // 删除已存在的统计
        statisticsMapper.delete(new LambdaQueryWrapper<DailyStatistics>()
                .eq(DailyStatistics::getStatDate, date));
        
        // 重新生成
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        
        DailyStatistics stats = new DailyStatistics();
        stats.setStatDate(date);
        
        // 使用指定日期的数据重新统计
        regenerateStatisticsByDate(stats, startOfDay, endOfDay);
        
        statisticsMapper.insert(stats);
        
        // 清理缓存
        clearStatisticsCache();
        
        log.info("补录统计数据完成: {}", date);
    }

    /**
     * 获取统计概览（管理员仪表盘）
     */
    @RedisRateLimit(key = "getStatisticsOverview", capacity = 10, refillRate = 10, duration = 1, timeUnit = TimeUnit.MINUTES)
    public StatisticsResponseDto getStatisticsOverview(StatisticsQueryDto queryDto) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        
        String cacheKey = CacheUtil.buildKey("statistics:overview", 
                String.valueOf(queryDto.getStartDate()), 
                String.valueOf(queryDto.getEndDate()),
                queryDto.getPeriod());
        
        StatisticsResponseDto cached = distributedCache.get(cacheKey, StatisticsResponseDto.class);
        if (cached != null) {
            return cached;
        }
        
        LocalDate endDate = queryDto.getEndDate() != null ? queryDto.getEndDate() : LocalDate.now();
        LocalDate startDate = queryDto.getStartDate() != null ? queryDto.getStartDate() : endDate.minusDays(7);
        
        List<DailyStatistics> statisticsList = statisticsMapper.selectByDateRange(startDate, endDate);
        
        StatisticsResponseDto response = StatisticsResponseDto.builder()
                .dailyStats(buildDailyStats(statisticsList.isEmpty() ? null : statisticsList.get(statisticsList.size() - 1)))
                .weeklyStats(buildWeeklyStats(statisticsList))
                .monthlyStats(buildMonthlyStats(statisticsList))
                .trendData(buildTrendData(statisticsList, startDate, endDate))
                .build();
        
        // 缓存10分钟
        distributedCache.put(cacheKey, response, 600);
        
        return response;
    }

    /**
     * 获取实时统计数据（今日实时）
     */
    public StatisticsResponseDto.DailyStatisticsDto getRealtimeStatistics() {
        String cacheKey = "statistics:realtime";
        
        StatisticsResponseDto.DailyStatisticsDto cached = distributedCache.get(cacheKey, StatisticsResponseDto.DailyStatisticsDto.class);
        if (cached != null) {
            return cached;
        }
        
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        
        StatisticsResponseDto.DailyStatisticsDto realtime = StatisticsResponseDto.DailyStatisticsDto.builder()
                .statDate(LocalDate.now())
                .newUserCount(countNewUsers(todayStart, now))
                .activeUserCount(countActiveUsers(todayStart, now))
                .dau(countActiveUsers(todayStart, now))
                .newPetPostCount(countNewPetPosts(todayStart, now))
                .newActivityCount(countNewActivities(todayStart, now))
                .newDailyPostCount(countNewDailyPosts(todayStart, now))
                .newCommentCount(countNewComments(todayStart, now))
                .newLikeCount(countNewLikes(todayStart, now))
                .newFollowCount(countNewFollows(todayStart, now))
                .newSignupCount(countNewSignups(todayStart, now))
                .newReportCount(countNewReports(todayStart, now))
                .pendingAuditCount(countPendingAudits())
                .build();
        
        // 缓存1分钟
        distributedCache.put(cacheKey, realtime, 60);
        
        return realtime;
    }

    // ==================== 私有统计方法 ====================

    private void fillUserStatistics(DailyStatistics stats, LocalDate statDate) {
        LocalDateTime startOfDay = statDate.atStartOfDay();
        LocalDateTime endOfDay = statDate.plusDays(1).atStartOfDay();
        
        // 新增用户数
        stats.setNewUserCount(countNewUsers(startOfDay, endOfDay));
        
        // 累计用户数
        stats.setTotalUserCount(Math.toIntExact(userMapper.selectCount(null)));
        
        // 活跃用户数（当日有操作的用户）
        stats.setActiveUserCount(countActiveUsers(startOfDay, endOfDay));
        
        // DAU = 活跃用户数
        stats.setDau(stats.getActiveUserCount());
        
        // WAU（过去7天活跃用户）
        LocalDateTime weekAgo = startOfDay.minusDays(7);
        stats.setWau(countActiveUsers(weekAgo, endOfDay));
        
        // MAU（过去30天活跃用户）
        LocalDateTime monthAgo = startOfDay.minusDays(30);
        stats.setMau(countActiveUsers(monthAgo, endOfDay));
    }

    private void fillContentStatistics(DailyStatistics stats, LocalDate statDate) {
        LocalDateTime startOfDay = statDate.atStartOfDay();
        LocalDateTime endOfDay = statDate.plusDays(1).atStartOfDay();
        
        // 宠物帖子
        stats.setNewPetPostCount(countNewPetPosts(startOfDay, endOfDay));
        stats.setTotalPetPostCount(Math.toIntExact(petPostMapper.selectCount(null)));
        
        // 活动
        stats.setNewActivityCount(countNewActivities(startOfDay, endOfDay));
        stats.setTotalActivityCount(Math.toIntExact(activityMapper.selectCount(null)));
        
        // 日常动态
        stats.setNewDailyPostCount(countNewDailyPosts(startOfDay, endOfDay));
        stats.setTotalDailyPostCount(Math.toIntExact(dailyPostMapper.selectCount(null)));
        
        // 评论
        stats.setNewCommentCount(countNewComments(startOfDay, endOfDay));
        stats.setTotalCommentCount(Math.toIntExact(commentMapper.selectCount(null)));
    }

    private void fillInteractionStatistics(DailyStatistics stats, LocalDate statDate) {
        LocalDateTime startOfDay = statDate.atStartOfDay();
        LocalDateTime endOfDay = statDate.plusDays(1).atStartOfDay();
        
        // 点赞
        stats.setNewLikeCount(countNewLikes(startOfDay, endOfDay));
        stats.setTotalLikeCount(Math.toIntExact(likeRecordMapper.selectCount(null)));
        
        // 关注
        stats.setNewFollowCount(countNewFollows(startOfDay, endOfDay));
        stats.setTotalFollowCount(Math.toIntExact(followMapper.selectCount(null)));
        
        // 收藏
        stats.setNewFavoriteCount(countNewFavorites(startOfDay, endOfDay));
        stats.setTotalFavoriteCount(Math.toIntExact(favoriteRecordMapper.selectCount(null)));
        
        // 分享（从各表统计）
        stats.setNewShareCount(countNewShares(startOfDay, endOfDay));
        stats.setTotalShareCount(countTotalShares());
    }

    private void fillActivityStatistics(DailyStatistics stats, LocalDate statDate) {
        LocalDateTime startOfDay = statDate.atStartOfDay();
        LocalDateTime endOfDay = statDate.plusDays(1).atStartOfDay();
        
        // 报名
        stats.setNewSignupCount(countNewSignups(startOfDay, endOfDay));
        stats.setTotalSignupCount(Math.toIntExact(activitySignupMapper.selectCount(null)));
        
        // 签到
        stats.setNewCheckinCount(countNewCheckins(startOfDay, endOfDay));
        stats.setTotalCheckinCount(countTotalCheckins());
    }

    private void fillAuditStatistics(DailyStatistics stats, LocalDate statDate) {
        // 待审核数量
        stats.setPendingAuditCount(countPendingAudits());
        
        // 审核通过数（当日）
        LocalDateTime startOfDay = statDate.atStartOfDay();
        LocalDateTime endOfDay = statDate.plusDays(1).atStartOfDay();
        stats.setApprovedCount(countAuditByStatus(startOfDay, endOfDay, 1));
        
        // 审核拒绝数（当日）
        stats.setRejectedCount(countAuditByStatus(startOfDay, endOfDay, 2));
    }

    private void fillReportStatistics(DailyStatistics stats, LocalDate statDate) {
        LocalDateTime startOfDay = statDate.atStartOfDay();
        LocalDateTime endOfDay = statDate.plusDays(1).atStartOfDay();
        
        // 新增举报数
        stats.setNewReportCount(countNewReports(startOfDay, endOfDay));
        
        // 待处理举报数
        stats.setPendingReportCount(countReportsByStatus(ReportStatus.PENDING.getCode()));
        
        // 已处理举报数
        stats.setHandledReportCount(countReportsByStatus(ReportStatus.HANDLED.getCode()));
    }

    private void fillMessageStatistics(DailyStatistics stats, LocalDate statDate) {
        LocalDateTime startOfDay = statDate.atStartOfDay();
        LocalDateTime endOfDay = statDate.plusDays(1).atStartOfDay();
        
        stats.setNewPrivateMessageCount(countNewPrivateMessages(startOfDay, endOfDay));
        stats.setTotalPrivateMessageCount(Math.toIntExact(privateMessageMapper.selectCount(null)));
    }

    private void regenerateStatisticsByDate(DailyStatistics stats, LocalDateTime start, LocalDateTime end) {
        stats.setNewUserCount(countNewUsers(start, end));
        stats.setTotalUserCount(Math.toIntExact(userMapper.selectCount(null)));
        stats.setActiveUserCount(countActiveUsers(start, end));
        stats.setDau(stats.getActiveUserCount());
        
        stats.setNewPetPostCount(countNewPetPosts(start, end));
        stats.setTotalPetPostCount(Math.toIntExact(petPostMapper.selectCount(null)));
        stats.setNewActivityCount(countNewActivities(start, end));
        stats.setTotalActivityCount(Math.toIntExact(activityMapper.selectCount(null)));
        stats.setNewDailyPostCount(countNewDailyPosts(start, end));
        stats.setTotalDailyPostCount(Math.toIntExact(dailyPostMapper.selectCount(null)));
        stats.setNewCommentCount(countNewComments(start, end));
        stats.setTotalCommentCount(Math.toIntExact(commentMapper.selectCount(null)));
        
        stats.setNewLikeCount(countNewLikes(start, end));
        stats.setTotalLikeCount(Math.toIntExact(likeRecordMapper.selectCount(null)));
        stats.setNewFollowCount(countNewFollows(start, end));
        stats.setTotalFollowCount(Math.toIntExact(followMapper.selectCount(null)));
        stats.setNewFavoriteCount(countNewFavorites(start, end));
        stats.setTotalFavoriteCount(Math.toIntExact(favoriteRecordMapper.selectCount(null)));
        
        stats.setNewSignupCount(countNewSignups(start, end));
        stats.setTotalSignupCount(Math.toIntExact(activitySignupMapper.selectCount(null)));
        
        stats.setPendingAuditCount(countPendingAudits());
        stats.setNewReportCount(countNewReports(start, end));
    }

    // ==================== 计数辅助方法 ====================

    private Integer countNewUsers(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(User::getCreateTime, start, end);
        return Math.toIntExact(userMapper.selectCount(wrapper));
    }

    private Integer countActiveUsers(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(User::getLastActiveTime, start, end);
        return Math.toIntExact(userMapper.selectCount(wrapper));
    }

    private Integer countNewPetPosts(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<PetPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(PetPost::getCreateTime, start, end);
        return Math.toIntExact(petPostMapper.selectCount(wrapper));
    }

    private Integer countNewActivities(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(Activity::getCreateTime, start, end);
        return Math.toIntExact(activityMapper.selectCount(wrapper));
    }

    private Integer countNewDailyPosts(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<DailyPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(DailyPost::getCreateTime, start, end);
        return Math.toIntExact(dailyPostMapper.selectCount(wrapper));
    }

    private Integer countNewComments(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(Comment::getCreateTime, start, end);
        return Math.toIntExact(commentMapper.selectCount(wrapper));
    }

    private Integer countNewLikes(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<LikeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(LikeRecord::getCreateTime, start, end);
        return Math.toIntExact(likeRecordMapper.selectCount(wrapper));
    }

    private Integer countNewFollows(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(Follow::getCreateTime, start, end);
        return Math.toIntExact(followMapper.selectCount(wrapper));
    }

    private Integer countNewFavorites(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<FavoriteRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(FavoriteRecord::getCreateTime, start, end);
        return Math.toIntExact(favoriteRecordMapper.selectCount(wrapper));
    }

    private Integer countNewShares(LocalDateTime start, LocalDateTime end) {
        // 分享计数可以从分享记录表统计，这里简化处理
        // 如果有专门的分享记录表，可以统计
        return 0;
    }

    private Integer countTotalShares() {
        // 累计分享数可以从各表统计
        return 0;
    }

    private Integer countNewSignups(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<ActivitySignup> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(ActivitySignup::getCreateTime, start, end);
        return Math.toIntExact(activitySignupMapper.selectCount(wrapper));
    }

    private Integer countNewCheckins(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<ActivitySignup> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(ActivitySignup::getCheckInTime, start, end);
        return Math.toIntExact(activitySignupMapper.selectCount(wrapper));
    }

    private Integer countTotalCheckins() {
        LambdaQueryWrapper<ActivitySignup> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(ActivitySignup::getCheckInTime);
        return Math.toIntExact(activitySignupMapper.selectCount(wrapper));
    }

    private Integer countPendingAudits() {
        LambdaQueryWrapper<AuditRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuditRecord::getAuditStatus, 0);
        return Math.toIntExact(auditRecordMapper.selectCount(wrapper));
    }

    private Integer countAuditByStatus(LocalDateTime start, LocalDateTime end, Integer status) {
        LambdaQueryWrapper<AuditRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(AuditRecord::getCreateTime, start, end);
        wrapper.eq(AuditRecord::getAuditStatus, status);
        return Math.toIntExact(auditRecordMapper.selectCount(wrapper));
    }

    private Integer countNewReports(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<ReportRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(ReportRecord::getCreateTime, start, end);
        return Math.toIntExact(reportRecordMapper.selectCount(wrapper));
    }

    private Integer countReportsByStatus(Integer status) {
        LambdaQueryWrapper<ReportRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportRecord::getStatus, status);
        return Math.toIntExact(reportRecordMapper.selectCount(wrapper));
    }

    private Integer countNewPrivateMessages(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<PrivateMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(PrivateMessage::getCreateTime, start, end);
        return Math.toIntExact(privateMessageMapper.selectCount(wrapper));
    }

    // ==================== 构建响应数据 ====================

    private StatisticsResponseDto.DailyStatisticsDto buildDailyStats(DailyStatistics stats) {
        if (stats == null) {
            return StatisticsResponseDto.DailyStatisticsDto.builder()
                    .statDate(LocalDate.now())
                    .newUserCount(0)
                    .activeUserCount(0)
                    .dau(0)
                    .newPetPostCount(0)
                    .newActivityCount(0)
                    .newDailyPostCount(0)
                    .newCommentCount(0)
                    .newLikeCount(0)
                    .newFollowCount(0)
                    .newSignupCount(0)
                    .newReportCount(0)
                    .pendingAuditCount(0)
                    .build();
        }
        
        return StatisticsResponseDto.DailyStatisticsDto.builder()
                .statDate(stats.getStatDate())
                .newUserCount(stats.getNewUserCount())
                .activeUserCount(stats.getActiveUserCount())
                .dau(stats.getDau())
                .newPetPostCount(stats.getNewPetPostCount())
                .newActivityCount(stats.getNewActivityCount())
                .newDailyPostCount(stats.getNewDailyPostCount())
                .newCommentCount(stats.getNewCommentCount())
                .newLikeCount(stats.getNewLikeCount())
                .newFollowCount(stats.getNewFollowCount())
                .newSignupCount(stats.getNewSignupCount())
                .newReportCount(stats.getNewReportCount())
                .pendingAuditCount(stats.getPendingAuditCount())
                .build();
    }

    private StatisticsResponseDto.WeeklyStatisticsDto buildWeeklyStats(List<DailyStatistics> list) {
        if (list.isEmpty()) {
            return StatisticsResponseDto.WeeklyStatisticsDto.builder()
                    .weekRange("")
                    .avgDailyActiveUsers(0)
                    .totalNewUsers(0)
                    .totalNewPosts(0)
                    .totalNewComments(0)
                    .totalNewLikes(0)
                    .weekOverWeekGrowth(0)
                    .build();
        }
        
        int avgActive = (int) list.stream()
                .mapToInt(DailyStatistics::getActiveUserCount)
                .average()
                .orElse(0);
        
        int totalNewUsers = list.stream().mapToInt(DailyStatistics::getNewUserCount).sum();
        int totalNewPosts = list.stream().mapToInt(s -> s.getNewPetPostCount() + s.getNewActivityCount() + s.getNewDailyPostCount()).sum();
        int totalNewComments = list.stream().mapToInt(DailyStatistics::getNewCommentCount).sum();
        int totalNewLikes = list.stream().mapToInt(DailyStatistics::getNewLikeCount).sum();
        
        return StatisticsResponseDto.WeeklyStatisticsDto.builder()
                .weekRange(list.get(0).getStatDate() + " ~ " + list.get(list.size() - 1).getStatDate())
                .avgDailyActiveUsers(avgActive)
                .totalNewUsers(totalNewUsers)
                .totalNewPosts(totalNewPosts)
                .totalNewComments(totalNewComments)
                .totalNewLikes(totalNewLikes)
                .weekOverWeekGrowth(0)
                .build();
    }

    private StatisticsResponseDto.MonthlyStatisticsDto buildMonthlyStats(List<DailyStatistics> list) {
        if (list.isEmpty()) {
            return StatisticsResponseDto.MonthlyStatisticsDto.builder()
                    .month("")
                    .totalActiveUsers(0)
                    .totalNewUsers(0)
                    .totalNewPosts(0)
                    .totalNewComments(0)
                    .monthOverMonthGrowth(0)
                    .build();
        }
        
        int totalActive = (int) list.stream().map(DailyStatistics::getActiveUserCount).distinct().count();
        int totalNewUsers = list.stream().mapToInt(DailyStatistics::getNewUserCount).sum();
        int totalNewPosts = list.stream().mapToInt(s -> s.getNewPetPostCount() + s.getNewActivityCount() + s.getNewDailyPostCount()).sum();
        int totalNewComments = list.stream().mapToInt(DailyStatistics::getNewCommentCount).sum();
        
        return StatisticsResponseDto.MonthlyStatisticsDto.builder()
                .month(list.get(0).getStatDate().format(DateTimeFormatter.ofPattern("yyyy-MM")))
                .totalActiveUsers(totalActive)
                .totalNewUsers(totalNewUsers)
                .totalNewPosts(totalNewPosts)
                .totalNewComments(totalNewComments)
                .monthOverMonthGrowth(0)
                .build();
    }

    private StatisticsResponseDto.TrendDataDto buildTrendData(List<DailyStatistics> list, LocalDate startDate, LocalDate endDate) {
        List<String> dates = new ArrayList<>();
        List<Integer> dauList = new ArrayList<>();
        List<Integer> newUserList = new ArrayList<>();
        List<Integer> newPostList = new ArrayList<>();
        List<Integer> newCommentList = new ArrayList<>();
        List<Integer> newLikeList = new ArrayList<>();
        
        // 填充缺失的日期
        Map<LocalDate, DailyStatistics> statsMap = list.stream()
                .collect(Collectors.toMap(DailyStatistics::getStatDate, s -> s));
        
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            dates.add(date.format(DateTimeFormatter.ofPattern("MM-dd")));
            
            DailyStatistics stats = statsMap.get(date);
            if (stats != null) {
                dauList.add(stats.getDau());
                newUserList.add(stats.getNewUserCount());
                newPostList.add(stats.getNewPetPostCount() + stats.getNewActivityCount() + stats.getNewDailyPostCount());
                newCommentList.add(stats.getNewCommentCount());
                newLikeList.add(stats.getNewLikeCount());
            } else {
                dauList.add(0);
                newUserList.add(0);
                newPostList.add(0);
                newCommentList.add(0);
                newLikeList.add(0);
            }
        }
        
        return StatisticsResponseDto.TrendDataDto.builder()
                .dates(dates)
                .dauList(dauList)
                .newUserList(newUserList)
                .newPostList(newPostList)
                .newCommentList(newCommentList)
                .newLikeList(newLikeList)
                .build();
    }

    private void clearStatisticsCache() {
        // 清理统计相关的所有缓存
        Set<String> keys = distributedCache.getInstance() instanceof org.springframework.data.redis.core.RedisTemplate ? 
                ((org.springframework.data.redis.core.RedisTemplate<String, Object>) distributedCache.getInstance()).keys("statistics:*") : null;
        if (keys != null && !keys.isEmpty()) {
            for (String key : keys) {
                distributedCache.delete(key);
            }
        }
    }
}