package com.hongjie.pms.modules.statistics.controller;

import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.common.pojo.CommonResult;
import com.hongjie.pms.modules.statistics.dto.StatisticsQueryDto;
import com.hongjie.pms.modules.statistics.dto.StatisticsResponseDto;
import com.hongjie.pms.modules.statistics.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 统计模块 - 管理员专用
 */
@Slf4j
@RestController
@RequestMapping("/pet-system/admin/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    /**
     * 统一统计查询入口
     *
     * 使用示例：
     * 1. 日报：GET /statistics/query?type=daily&date=2024-01-15
     * 2. 周报：GET /statistics/query?type=weekly&date=2024-01-15
     * 3. 月报：GET /statistics/query?type=monthly&month=2024-01
     * 4. 年报：GET /statistics/query?type=yearly&year=2024
     * 5. 自定义范围：GET /statistics/query?type=range&startDate=2024-01-01&endDate=2024-01-31
     * 6. 今日实时：GET /statistics/realtime
     */
    @GetMapping("/query")
    public CommonResult<?> query(StatisticsQueryDto queryDto) {
        checkAdmin();

        String type = queryDto.getType();

        if (type == null || type.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "统计类型不能为空");
        }

        switch (type) {
            case "daily":
                LocalDate date = queryDto.getDate() != null ? queryDto.getDate() : LocalDate.now();
                return CommonResult.success(statisticsService.getDailyStatistics(date));

            case "weekly":
                LocalDate weekDate = queryDto.getDate() != null ? queryDto.getDate() : LocalDate.now();
                return CommonResult.success(statisticsService.getWeeklyStatistics(weekDate));

            case "monthly":
                String month = queryDto.getMonth() != null ? queryDto.getMonth()
                        : LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
                return CommonResult.success(statisticsService.getMonthlyStatistics(month));

            case "yearly":
                int year = queryDto.getYear() != null ? queryDto.getYear() : LocalDate.now().getYear();
                return CommonResult.success(statisticsService.getYearlyStatistics(year));

            case "range":
                LocalDate startDate = queryDto.getStartDate() != null ? queryDto.getStartDate() : LocalDate.now().minusDays(7);
                LocalDate endDate = queryDto.getEndDate() != null ? queryDto.getEndDate() : LocalDate.now();
                return CommonResult.success(statisticsService.getRangeStatistics(startDate, endDate));

            default:
                throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的统计类型: " + type);
        }
    }

    /**
     * 获取实时统计数据（今日实时）
     */
    @GetMapping("/realtime")
    public CommonResult<StatisticsResponseDto.DailyStatisticsDto> getRealtime() {
        checkAdmin();
        return CommonResult.success(statisticsService.getRealtimeStatistics());
    }

    /**
     * 补录指定日期的统计数据
     */
    @PostMapping("/regenerate")
    public CommonResult<String> regenerateStatistics(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        checkAdmin();
        statisticsService.regenerateStatistics(date);
        return CommonResult.success("数据补录成功");
    }

    /**
     * 批量补录指定日期范围的统计数据
     */
    @PostMapping("/regenerate/range")
    public CommonResult<String> regenerateStatisticsRange(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        checkAdmin();
        statisticsService.regenerateStatisticsRange(startDate, endDate);
        return CommonResult.success("批量补录成功");
    }

    /**
     * 清理统计缓存
     */
    @DeleteMapping("/cache")
    public CommonResult<String> clearCache() {
        checkAdmin();
        statisticsService.clearAllStatisticsCache();
        return CommonResult.success("缓存清理成功");
    }

    private void checkAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}