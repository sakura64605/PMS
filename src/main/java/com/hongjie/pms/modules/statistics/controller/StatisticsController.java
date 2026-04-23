package com.hongjie.pms.modules.statistics.controller;

import com.hongjie.pms.common.annotation.RedisRateLimit;
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
import java.util.concurrent.TimeUnit;

/**
 * 统计模块
 */
@Slf4j
@RestController
@RequestMapping("/pet-system/admin/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    /**
     * 获取统计概览
     */
    @GetMapping("/overview")
    public CommonResult<StatisticsResponseDto> getOverview(StatisticsQueryDto queryDto) {
        checkAdmin();
        StatisticsResponseDto response = statisticsService.getStatisticsOverview(queryDto);
        return CommonResult.success(response);
    }

    /**
     * 获取实时统计数据
     */
    @GetMapping("/realtime")
    public CommonResult<StatisticsResponseDto.DailyStatisticsDto> getRealtime() {
        checkAdmin();
        StatisticsResponseDto.DailyStatisticsDto realtime = statisticsService.getRealtimeStatistics();
        return CommonResult.success(realtime);
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

    private void checkAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}