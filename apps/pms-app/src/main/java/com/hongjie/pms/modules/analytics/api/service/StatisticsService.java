package com.hongjie.pms.modules.analytics.api.service;

import com.hongjie.pms.modules.analytics.api.dto.StatisticsResponseDto;
import java.time.LocalDate;
import java.util.*;

/** Runtime contract implemented by the migrated service. */
public interface StatisticsService {
    StatisticsResponseDto.DailyStatisticsDto getDailyStatistics(LocalDate date);

    StatisticsResponseDto.WeeklyStatisticsDto getWeeklyStatistics(LocalDate date);

    StatisticsResponseDto.MonthlyStatisticsDto getMonthlyStatistics(String month);

    StatisticsResponseDto.YearlyStatisticsDto getYearlyStatistics(int year);

    StatisticsResponseDto.RangeStatisticsDto getRangeStatistics(
            LocalDate startDate, LocalDate endDate);

    StatisticsResponseDto.DailyStatisticsDto getRealtimeStatistics();

    void generateDailyStatistics();

    void compensateMissingStatistics();

    void regenerateStatistics(LocalDate date);

    void regenerateStatisticsRange(LocalDate startDate, LocalDate endDate);

    void clearAllStatisticsCache();
}
