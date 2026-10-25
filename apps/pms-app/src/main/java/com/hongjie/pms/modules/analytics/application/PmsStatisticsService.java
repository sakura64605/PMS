package com.hongjie.pms.modules.analytics.application;
import com.hongjie.pms.modules.activity.infrastructure.persistence.mapper.*;
import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.mapper.*;
import com.hongjie.pms.modules.analytics.api.service.StatisticsService;
import com.hongjie.pms.modules.analytics.api.dto.StatisticsResponseDto.*;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

/** Compatibility facade for the existing controllers and XXL-JOB actions. */
@Service @Primary
public class PmsStatisticsService implements StatisticsService {
    private final StatisticsQueries queries;
    private final StatisticsGeneration generation;
    private final StatisticsCache cache;
    public PmsStatisticsService(StatisticsQueries queries, StatisticsGeneration generation, StatisticsCache cache) {

        this.queries=queries;this.generation=generation;this.cache=cache;
    }
    @Override public DailyStatisticsDto getDailyStatistics(LocalDate date){return queries.daily(date);}
    @Override public WeeklyStatisticsDto getWeeklyStatistics(LocalDate date){return queries.weekly(date);}
    @Override public MonthlyStatisticsDto getMonthlyStatistics(String month){return queries.monthly(month);}
    @Override public YearlyStatisticsDto getYearlyStatistics(int year){return queries.yearly(year);}
    @Override public RangeStatisticsDto getRangeStatistics(LocalDate start,LocalDate end){return queries.range(start,end);}
    @Override public DailyStatisticsDto getRealtimeStatistics(){return queries.realtime();}
    @Override public void generateDailyStatistics(){generation.daily();}
    @Override public void compensateMissingStatistics(){generation.compensate();}
    @Override public void regenerateStatistics(LocalDate date){generation.regenerate(date);}
    @Override public void regenerateStatisticsRange(LocalDate start,LocalDate end){generation.regenerateRange(start,end);}
    @Override public void clearAllStatisticsCache(){
        if(!UserContext.isAdmin())throw new BusinessException(ErrorCode.FORBIDDEN);
        cache.afterCommit();
    }
}
