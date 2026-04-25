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
import com.hongjie.pms.modules.report.mapper.ReportRecordMapper;
import com.hongjie.pms.modules.statistics.dto.StatisticsResponseDto;
import com.hongjie.pms.modules.statistics.entity.DailyStatistics;
import com.hongjie.pms.modules.statistics.mapper.DailyStatisticsMapper;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
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
     * 获取日报数据
     */
    public StatisticsResponseDto.DailyStatisticsDto getDailyStatistics(LocalDate date) {
        String cacheKey = CacheUtil.buildKey("statistics:daily", date.toString());

        StatisticsResponseDto.DailyStatisticsDto cached = distributedCache.get(cacheKey, StatisticsResponseDto.DailyStatisticsDto.class);
        if (cached != null) {
            return cached;
        }

        DailyStatistics stats = statisticsMapper.selectByDate(date);
        StatisticsResponseDto.DailyStatisticsDto result = buildDailyStats(stats, date);

        // 获取最近30天趋势
        LocalDate startDate = date.minusDays(30);
        LocalDate endDate = date;
        List<DailyStatistics> trendStats = statisticsMapper.selectByDateRange(startDate, endDate);
        result.setTrendData(buildTrendData(trendStats, startDate, endDate));

        distributedCache.put(cacheKey, result, 3600);
        return result;
    }

    /**
     * 获取周报数据
     */
    public StatisticsResponseDto.WeeklyStatisticsDto getWeeklyStatistics(LocalDate date) {
        LocalDate monday = date.with(java.time.DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);

        String cacheKey = CacheUtil.buildKey("statistics:weekly", monday.toString());

        StatisticsResponseDto.WeeklyStatisticsDto cached = distributedCache.get(cacheKey, StatisticsResponseDto.WeeklyStatisticsDto.class);
        if (cached != null) {
            return cached;
        }

        List<DailyStatistics> weekStats = statisticsMapper.selectByDateRange(monday, sunday);
        StatisticsResponseDto.WeeklyStatisticsDto result = buildWeeklyStats(weekStats, monday, sunday);

        result.setTrendData(buildTrendData(weekStats, monday, sunday));

        distributedCache.put(cacheKey, result, 7200);
        return result;
    }

    /**
     * 获取月报数据
     */
    public StatisticsResponseDto.MonthlyStatisticsDto getMonthlyStatistics(String month) {
        YearMonth yearMonth = YearMonth.parse(month);
        LocalDate firstDay = yearMonth.atDay(1);
        LocalDate lastDay = yearMonth.atEndOfMonth();

        String cacheKey = CacheUtil.buildKey("statistics:monthly", month);

        StatisticsResponseDto.MonthlyStatisticsDto cached = distributedCache.get(cacheKey, StatisticsResponseDto.MonthlyStatisticsDto.class);
        if (cached != null) {
            return cached;
        }

        List<DailyStatistics> monthStats = statisticsMapper.selectByDateRange(firstDay, lastDay);
        StatisticsResponseDto.MonthlyStatisticsDto result = buildMonthlyStats(monthStats, month, firstDay);

        // 获取最近6个月趋势（包含当前月前后）
        LocalDate startDate = firstDay.minusMonths(6);
        LocalDate endDate = lastDay;
        List<DailyStatistics> trendStats = statisticsMapper.selectByDateRange(startDate, endDate);
        result.setTrendData(buildTrendData(monthStats, firstDay, lastDay));

        distributedCache.put(cacheKey, result, 14400);
        return result;
    }

    /**
     * 获取年报数据
     */
    public StatisticsResponseDto.YearlyStatisticsDto getYearlyStatistics(int year) {
        String cacheKey = CacheUtil.buildKey("statistics:yearly", String.valueOf(year));

        StatisticsResponseDto.YearlyStatisticsDto cached = distributedCache.get(cacheKey, StatisticsResponseDto.YearlyStatisticsDto.class);
        if (cached != null) {
            return cached;
        }

        List<DailyStatistics> yearStats = statisticsMapper.selectByYear(year);
        StatisticsResponseDto.YearlyStatisticsDto result = buildYearlyStats(yearStats, year);

        // 获取最近2年趋势（包含当前年前后）
        LocalDate startDate = LocalDate.of(year - 1, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);
        List<DailyStatistics> trendStats = statisticsMapper.selectByDateRange(startDate, endDate);
        result.setTrendData(buildTrendData(yearStats, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)));

        distributedCache.put(cacheKey, result, 86400);
        return result;
    }

    /**
     * 获取自定义范围统计数据
     */
    public StatisticsResponseDto.RangeStatisticsDto getRangeStatistics(LocalDate startDate, LocalDate endDate) {
        String cacheKey = CacheUtil.buildKey("statistics:range", startDate.toString(), endDate.toString());

        StatisticsResponseDto.RangeStatisticsDto cached = distributedCache.get(cacheKey, StatisticsResponseDto.RangeStatisticsDto.class);
        if (cached != null) {
            return cached;
        }

        List<DailyStatistics> statisticsList = statisticsMapper.selectByDateRange(startDate, endDate);

        // 构建区间汇总数据
        StatisticsResponseDto.RangeStatisticsDto result = buildRangeStatistics(statisticsList, startDate, endDate);

        // 构建趋势数据（用于图表）
        result.setTrendData(buildTrendData(statisticsList, startDate, endDate));

        distributedCache.put(cacheKey, result, 1800);
        return result;
    }

    /**
     * 构建区间汇总数据
     */
    private StatisticsResponseDto.RangeStatisticsDto buildRangeStatistics(List<DailyStatistics> list, LocalDate startDate, LocalDate endDate) {
        if (list == null || list.isEmpty()) {
            return StatisticsResponseDto.RangeStatisticsDto.builder()
                    .startDate(startDate)
                    .endDate(endDate)
                    .totalNewUsers(0)
                    .avgActiveUsers(0)
                    .maxDau(0)
                    .minDau(0)
                    .totalNewPosts(0)
                    .totalNewComments(0)
                    .totalNewLikes(0)
                    .build();
        }

        int totalNewUsers = list.stream().mapToInt(DailyStatistics::getNewUserCount).sum();
        int avgActiveUsers = (int) list.stream().mapToInt(DailyStatistics::getActiveUserCount).average().orElse(0);
        int maxDau = list.stream().mapToInt(DailyStatistics::getDau).max().orElse(0);
        int minDau = list.stream().mapToInt(DailyStatistics::getDau).min().orElse(0);
        int totalNewPosts = list.stream().mapToInt(s -> s.getNewPetPostCount() + s.getNewActivityCount() + s.getNewDailyPostCount()).sum();
        int totalNewComments = list.stream().mapToInt(DailyStatistics::getNewCommentCount).sum();
        int totalNewLikes = list.stream().mapToInt(DailyStatistics::getNewLikeCount).sum();

        return StatisticsResponseDto.RangeStatisticsDto.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalNewUsers(totalNewUsers)
                .avgActiveUsers(avgActiveUsers)
                .maxDau(maxDau)
                .minDau(minDau)
                .totalNewPosts(totalNewPosts)
                .totalNewComments(totalNewComments)
                .totalNewLikes(totalNewLikes)
                .build();
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

        distributedCache.put(cacheKey, realtime, 60);
        return realtime;
    }

    // ==================== 定时任务 ====================

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
            DailyStatistics existing = statisticsMapper.selectByDate(statDate);
            if (existing != null) {
                log.info("{} 的数据统计已存在，跳过生成", statDate);
                return;
            }

            DailyStatistics stats = new DailyStatistics();
            stats.setStatDate(statDate);

            fillUserStatistics(stats, statDate);
            fillContentStatistics(stats, statDate);
            fillInteractionStatistics(stats, statDate);
            fillActivityStatistics(stats, statDate);
            fillAuditStatistics(stats, statDate);
            fillReportStatistics(stats, statDate);
            fillMessageStatistics(stats, statDate);

            statisticsMapper.insert(stats);

            clearAllStatisticsCache();

            log.info("{} 的数据统计生成完成，耗时 {} ms", statDate, System.currentTimeMillis() - startTime);

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

        statisticsMapper.delete(new LambdaQueryWrapper<DailyStatistics>()
                .eq(DailyStatistics::getStatDate, date));

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        DailyStatistics stats = new DailyStatistics();
        stats.setStatDate(date);

        regenerateStatisticsByDate(stats, startOfDay, endOfDay);

        statisticsMapper.insert(stats);
        clearAllStatisticsCache();

        log.info("补录统计数据完成: {}", date);
    }

    @Transactional
    public void regenerateStatisticsRange(LocalDate startDate, LocalDate endDate) {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "开始日期不能晚于结束日期");
        }

        log.info("开始批量补录统计数据，日期范围: {} 至 {}", startDate, endDate);
        long startTime = System.currentTimeMillis();

        int successCount = 0;
        int failCount = 0;

        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            try {
                // 删除已存在的统计
                statisticsMapper.delete(new LambdaQueryWrapper<DailyStatistics>()
                        .eq(DailyStatistics::getStatDate, currentDate));

                // 重新生成
                LocalDateTime startOfDay = currentDate.atStartOfDay();
                LocalDateTime endOfDay = currentDate.plusDays(1).atStartOfDay();

                DailyStatistics stats = new DailyStatistics();
                stats.setStatDate(currentDate);
                regenerateStatisticsByDate(stats, startOfDay, endOfDay);

                statisticsMapper.insert(stats);
                successCount++;

                log.debug("补录成功: {}", currentDate);
            } catch (Exception e) {
                failCount++;
                log.error("补录失败: {}", currentDate, e);
            }
            currentDate = currentDate.plusDays(1);
        }

        // 清理缓存
        clearAllStatisticsCache();

        long endTime = System.currentTimeMillis();
        log.info("批量补录完成，成功: {}, 失败: {}, 耗时: {} ms",
                successCount, failCount, endTime - startTime);

        if (failCount > 0) {
            throw new BusinessException("批量补录部分失败，成功: " + successCount + "，失败: " + failCount);
        }
    }

    /**
     * 清理所有统计缓存
     */
    public void clearAllStatisticsCache() {
        try {
            RedisTemplate<String, Object> redisTemplate = (RedisTemplate<String, Object>) distributedCache.getInstance();
            Set<String> keys = redisTemplate.keys("statistics:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("清理统计缓存完成，共清理 {} 个缓存", keys.size());
            }
        } catch (Exception e) {
            log.warn("清理统计缓存失败: {}", e.getMessage());
        }
    }

    // ==================== 私有统计填充方法 ====================

    private void fillUserStatistics(DailyStatistics stats, LocalDate statDate) {
        LocalDateTime startOfDay = statDate.atStartOfDay();
        LocalDateTime endOfDay = statDate.plusDays(1).atStartOfDay();

        stats.setNewUserCount(countNewUsers(startOfDay, endOfDay));
        stats.setTotalUserCount(Math.toIntExact(userMapper.selectCount(null)));
        stats.setActiveUserCount(countActiveUsers(startOfDay, endOfDay));
        stats.setDau(stats.getActiveUserCount());

        LocalDateTime weekAgo = startOfDay.minusDays(7);
        stats.setWau(countActiveUsers(weekAgo, endOfDay));

        LocalDateTime monthAgo = startOfDay.minusDays(30);
        stats.setMau(countActiveUsers(monthAgo, endOfDay));
    }

    private void fillContentStatistics(DailyStatistics stats, LocalDate statDate) {
        LocalDateTime startOfDay = statDate.atStartOfDay();
        LocalDateTime endOfDay = statDate.plusDays(1).atStartOfDay();

        stats.setNewPetPostCount(countNewPetPosts(startOfDay, endOfDay));
        stats.setTotalPetPostCount(Math.toIntExact(petPostMapper.selectCount(null)));
        stats.setNewActivityCount(countNewActivities(startOfDay, endOfDay));
        stats.setTotalActivityCount(Math.toIntExact(activityMapper.selectCount(null)));
        stats.setNewDailyPostCount(countNewDailyPosts(startOfDay, endOfDay));
        stats.setTotalDailyPostCount(Math.toIntExact(dailyPostMapper.selectCount(null)));
        stats.setNewCommentCount(countNewComments(startOfDay, endOfDay));
        stats.setTotalCommentCount(Math.toIntExact(commentMapper.selectCount(null)));
    }

    private void fillInteractionStatistics(DailyStatistics stats, LocalDate statDate) {
        LocalDateTime startOfDay = statDate.atStartOfDay();
        LocalDateTime endOfDay = statDate.plusDays(1).atStartOfDay();

        stats.setNewLikeCount(countNewLikes(startOfDay, endOfDay));
        stats.setTotalLikeCount(Math.toIntExact(likeRecordMapper.selectCount(null)));
        stats.setNewFollowCount(countNewFollows(startOfDay, endOfDay));
        stats.setTotalFollowCount(Math.toIntExact(followMapper.selectCount(null)));
        stats.setNewFavoriteCount(countNewFavorites(startOfDay, endOfDay));
        stats.setTotalFavoriteCount(Math.toIntExact(favoriteRecordMapper.selectCount(null)));
        stats.setNewShareCount(0);
        stats.setTotalShareCount(0);
    }

    private void fillActivityStatistics(DailyStatistics stats, LocalDate statDate) {
        LocalDateTime startOfDay = statDate.atStartOfDay();
        LocalDateTime endOfDay = statDate.plusDays(1).atStartOfDay();

        stats.setNewSignupCount(countNewSignups(startOfDay, endOfDay));
        stats.setTotalSignupCount(Math.toIntExact(activitySignupMapper.selectCount(null)));
        stats.setNewCheckinCount(countNewCheckins(startOfDay, endOfDay));
        stats.setTotalCheckinCount(countTotalCheckins());
    }

    private void fillAuditStatistics(DailyStatistics stats, LocalDate statDate) {
        stats.setPendingAuditCount(countPendingAudits());

        LocalDateTime startOfDay = statDate.atStartOfDay();
        LocalDateTime endOfDay = statDate.plusDays(1).atStartOfDay();
        stats.setApprovedCount(countAuditByStatus(startOfDay, endOfDay, 1));
        stats.setRejectedCount(countAuditByStatus(startOfDay, endOfDay, 2));
    }

    private void fillReportStatistics(DailyStatistics stats, LocalDate statDate) {
        LocalDateTime startOfDay = statDate.atStartOfDay();
        LocalDateTime endOfDay = statDate.plusDays(1).atStartOfDay();

        stats.setNewReportCount(countNewReports(startOfDay, endOfDay));
        stats.setPendingReportCount(countReportsByStatus(0));
        stats.setHandledReportCount(countReportsByStatus(2));
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

    // ==================== 构建响应数据方法 ====================

    private StatisticsResponseDto.DailyStatisticsDto buildDailyStats(DailyStatistics stats, LocalDate defaultDate) {
        if (stats == null) {
            return StatisticsResponseDto.DailyStatisticsDto.builder()
                    .statDate(defaultDate)
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

    private StatisticsResponseDto.WeeklyStatisticsDto buildWeeklyStats(List<DailyStatistics> list, LocalDate startDate, LocalDate endDate) {
        if (list == null || list.isEmpty()) {
            return StatisticsResponseDto.WeeklyStatisticsDto.builder()
                    .weekRange(startDate + " ~ " + endDate)
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
                .weekOverWeekGrowth(calculateWeekOverWeekGrowth(list))
                .build();
    }

    private StatisticsResponseDto.WeeklyStatisticsDto buildWeeklyStats(List<DailyStatistics> list) {
        if (list == null || list.isEmpty()) {
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
        return buildWeeklyStats(list, list.get(0).getStatDate(), list.get(list.size() - 1).getStatDate());
    }

    private StatisticsResponseDto.MonthlyStatisticsDto buildMonthlyStats(List<DailyStatistics> list, String month, LocalDate defaultDate) {
        if (list == null || list.isEmpty()) {
            String targetMonth = month != null ? month : defaultDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            return StatisticsResponseDto.MonthlyStatisticsDto.builder()
                    .month(targetMonth)
                    .totalActiveUsers(0)
                    .totalNewUsers(0)
                    .totalNewPosts(0)
                    .totalNewComments(0)
                    .monthOverMonthGrowth(0)
                    .avgDailyActiveUsers(0)
                    .build();
        }

        int totalNewUsers = list.stream().mapToInt(DailyStatistics::getNewUserCount).sum();
        int totalNewPosts = list.stream().mapToInt(s -> s.getNewPetPostCount() + s.getNewActivityCount() + s.getNewDailyPostCount()).sum();
        int totalNewComments = list.stream().mapToInt(DailyStatistics::getNewCommentCount).sum();
        int avgDailyActive = (int) list.stream().mapToInt(DailyStatistics::getActiveUserCount).average().orElse(0);

        int totalActive = calculateMonthlyActiveUsers(list.get(0).getStatDate());
        int monthOverMonthGrowth = calculateMonthOverMonthGrowth(list.get(0).getStatDate(), totalNewUsers);

        String targetMonth = month != null ? month : list.get(0).getStatDate().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        return StatisticsResponseDto.MonthlyStatisticsDto.builder()
                .month(targetMonth)
                .totalActiveUsers(totalActive)
                .totalNewUsers(totalNewUsers)
                .totalNewPosts(totalNewPosts)
                .totalNewComments(totalNewComments)
                .monthOverMonthGrowth(monthOverMonthGrowth)
                .avgDailyActiveUsers(avgDailyActive)
                .build();
    }

    private StatisticsResponseDto.YearlyStatisticsDto buildYearlyStats(List<DailyStatistics> list, int year) {
        if (list == null || list.isEmpty()) {
            return StatisticsResponseDto.YearlyStatisticsDto.builder()
                    .year(year)
                    .totalNewUsers(0)
                    .totalNewPosts(0)
                    .totalActiveUsers(0)
                    .yearOverYearGrowth(0)
                    .avgMonthlyActiveUsers(0)
                    .build();
        }

        int totalNewUsers = list.stream().mapToInt(DailyStatistics::getNewUserCount).sum();
        int totalNewPosts = list.stream().mapToInt(s -> s.getNewPetPostCount() + s.getNewActivityCount() + s.getNewDailyPostCount()).sum();
        int avgMonthlyActive = (int) list.stream().collect(Collectors.groupingBy(
                s -> s.getStatDate().getMonth(),
                Collectors.summingInt(DailyStatistics::getActiveUserCount)
        )).values().stream().mapToInt(Integer::intValue).average().orElse(0);

        int totalActive = calculateYearlyActiveUsers(year);
        int yearOverYearGrowth = calculateYearOverYearGrowth(year, totalNewUsers);

        return StatisticsResponseDto.YearlyStatisticsDto.builder()
                .year(year)
                .totalNewUsers(totalNewUsers)
                .totalNewPosts(totalNewPosts)
                .totalActiveUsers(totalActive)
                .yearOverYearGrowth(yearOverYearGrowth)
                .avgMonthlyActiveUsers((int) avgMonthlyActive)
                .build();
    }

    private StatisticsResponseDto.TrendDataDto buildTrendData(List<DailyStatistics> list, LocalDate startDate, LocalDate endDate) {
        List<String> dates = new ArrayList<>();
        List<Integer> dauList = new ArrayList<>();
        List<Integer> newUserList = new ArrayList<>();
        List<Integer> newPostList = new ArrayList<>();
        List<Integer> newCommentList = new ArrayList<>();
        List<Integer> newLikeList = new ArrayList<>();

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

    // ==================== 环比/同比计算方法 ====================

    private int calculateWeekOverWeekGrowth(List<DailyStatistics> currentWeek) {
        if (currentWeek == null || currentWeek.isEmpty()) {
            return 0;
        }

        try {
            LocalDate currentMonday = currentWeek.get(0).getStatDate().with(java.time.DayOfWeek.MONDAY);
            LocalDate previousMonday = currentMonday.minusWeeks(1);
            LocalDate previousSunday = previousMonday.plusDays(6);

            List<DailyStatistics> previousWeek = statisticsMapper.selectByDateRange(previousMonday, previousSunday);

            int currentTotal = currentWeek.stream().mapToInt(DailyStatistics::getActiveUserCount).sum();
            int previousTotal = previousWeek.stream().mapToInt(DailyStatistics::getActiveUserCount).sum();

            if (previousTotal == 0) {
                return currentTotal > 0 ? 100 : 0;
            }
            return (int) ((currentTotal - previousTotal) * 100.0 / previousTotal);
        } catch (Exception e) {
            log.warn("计算周环比失败: {}", e.getMessage());
            return 0;
        }
    }

    private int calculateMonthOverMonthGrowth(LocalDate currentMonthFirstDay, int currentTotal) {
        try {
            LocalDate previousMonthFirstDay = currentMonthFirstDay.minusMonths(1);
            LocalDate previousMonthLastDay = previousMonthFirstDay.plusMonths(1).minusDays(1);

            List<DailyStatistics> previousStats = statisticsMapper.selectByDateRange(previousMonthFirstDay, previousMonthLastDay);
            int previousTotal = previousStats.stream().mapToInt(DailyStatistics::getNewUserCount).sum();

            if (previousTotal == 0) {
                return currentTotal > 0 ? 100 : 0;
            }
            return (int) ((currentTotal - previousTotal) * 100.0 / previousTotal);
        } catch (Exception e) {
            log.warn("计算月环比失败: {}", e.getMessage());
            return 0;
        }
    }

    private int calculateYearOverYearGrowth(int currentYear, int currentTotal) {
        try {
            int previousYear = currentYear - 1;
            List<DailyStatistics> previousStats = statisticsMapper.selectByYear(previousYear);
            int previousTotal = previousStats.stream().mapToInt(DailyStatistics::getNewUserCount).sum();

            if (previousTotal == 0) {
                return currentTotal > 0 ? 100 : 0;
            }
            return (int) ((currentTotal - previousTotal) * 100.0 / previousTotal);
        } catch (Exception e) {
            log.warn("计算年同比失败: {}", e.getMessage());
            return 0;
        }
    }

    private int calculateMonthlyActiveUsers(LocalDate date) {
        LocalDateTime startOfMonth = date.withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfMonth = startOfMonth.plusMonths(1);
        return countActiveUsers(startOfMonth, endOfMonth);
    }

    private int calculateYearlyActiveUsers(int year) {
        LocalDateTime startOfYear = LocalDateTime.of(year, 1, 1, 0, 0);
        LocalDateTime endOfYear = LocalDateTime.of(year + 1, 1, 1, 0, 0);
        return countActiveUsers(startOfYear, endOfYear);
    }
}