package com.hongjie.pms.modules.analytics.application;

import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.analytics.api.dto.StatisticsResponseDto.*;
import com.hongjie.pms.modules.analytics.infrastructure.persistence.entity.DailyStatistics;
import com.hongjie.pms.modules.analytics.infrastructure.persistence.mapper.DailyStatisticsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import static com.hongjie.pms.modules.analytics.application.StatisticsAssembler.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class StatisticsQueries {
    private final DailyStatisticsMapper snapshots;
    private final StatisticsFactMapper facts;
    private final StatisticsCollector collector;
    private final StatisticsCalendar calendar;
    private final StatisticsAssembler assembler;

    public DailyStatisticsDto daily(LocalDate date) {
        admin(); StatisticsDates.date(date);
        var trend = rows(date.minusDays(29), date);
        return assembler.daily(trend.get(trend.size()-1), trend);
    }
    public DailyStatisticsDto realtime() { return daily(calendar.today()); }
    public RangeStatisticsDto range(LocalDate start, LocalDate end) {
        admin(); StatisticsDates.range(start,end,3660);
        return assembler.range(rows(start,end));
    }
    public WeeklyStatisticsDto weekly(LocalDate date) {
        admin(); StatisticsDates.date(date);
        LocalDate start=date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), end=start.plusDays(6);
        var current=rows(start,end); var previous=rows(start.minusWeeks(1),end.minusWeeks(1));
        return WeeklyStatisticsDto.builder().weekRange(start+" ~ "+end)
                .avgDailyActiveUsers(assembler.average(current))
                .totalNewUsers(assembler.sum(current,r->n(r.getNewUserCount())))
                .totalNewPosts(assembler.sum(current,StatisticsAssembler::posts))
                .totalNewComments(assembler.sum(current,r->n(r.getNewCommentCount())))
                .totalNewLikes(assembler.sum(current,r->n(r.getNewLikeCount())))
                .weekOverWeekGrowth(assembler.growth(assembler.sum(current,r->n(r.getDau())),assembler.sum(previous,r->n(r.getDau()))))
                .trendData(assembler.trend(current)).build();
    }
    public MonthlyStatisticsDto monthly(String value) {
        admin(); YearMonth month=StatisticsDates.month(value), previous=month.minusMonths(1);
        var current=rows(month.atDay(1),month.atEndOfMonth());
        int users=assembler.sum(current,r->n(r.getNewUserCount()));
        return MonthlyStatisticsDto.builder().month(month.toString()).totalActiveUsers(active(month.atDay(1),month.atEndOfMonth()))
                .totalNewUsers(users).totalNewPosts(assembler.sum(current,StatisticsAssembler::posts))
                .totalNewComments(assembler.sum(current,r->n(r.getNewCommentCount())))
                .monthOverMonthGrowth(assembler.growth(users,assembler.sum(rows(previous.atDay(1),previous.atEndOfMonth()),r->n(r.getNewUserCount()))))
                .avgDailyActiveUsers(assembler.average(current)).trendData(assembler.trend(current)).build();
    }
    public YearlyStatisticsDto yearly(int year) {
        admin();
        if(year<1001 || year>9998) throw new BusinessException(ErrorCode.PARAM_ERROR,"统计年份无效");
        LocalDate start=LocalDate.of(year,1,1),end=LocalDate.of(year,12,31);
        var current=rows(start,end); int users=assembler.sum(current,r->n(r.getNewUserCount()));
        int active=active(start,end);
        return YearlyStatisticsDto.builder().year(year).totalNewUsers(users)
                .totalNewPosts(assembler.sum(current,StatisticsAssembler::posts)).totalActiveUsers(active)
                .yearOverYearGrowth(assembler.growth(users,assembler.sum(rows(start.minusYears(1),end.minusYears(1)),r->n(r.getNewUserCount()))))
                .avgMonthlyActiveUsers(active/12).trendData(assembler.trend(current)).build();
    }
    private int active(LocalDate start, LocalDate end) {
        LocalDateTime cutoff=end.plusDays(1).atStartOfDay(), now=calendar.now();
        if(cutoff.isAfter(now))cutoff=now;
        return cutoff.isBefore(start.atStartOfDay()) ? 0 : facts.active(start.atStartOfDay(),cutoff);
    }
    private List<DailyStatistics> rows(LocalDate start,LocalDate end) {
        Map<LocalDate,DailyStatistics> byDate=new HashMap<>();
        for(var row:snapshots.selectByDateRange(start,end)) byDate.put(row.getStatDate(),row);
        LocalDate today=calendar.today();
        if(!today.isBefore(start)&&!today.isAfter(end))byDate.put(today,collector.collect(today,calendar.now()));
        List<DailyStatistics> result=new ArrayList<>();
        for(LocalDate date=start;!date.isAfter(end);date=date.plusDays(1)){
            DailyStatistics row=byDate.get(date);
            if(row==null){row=new DailyStatistics();row.setStatDate(date);}
            result.add(row);
        }
        return result;
    }
    private void admin(){if(!UserContext.isAdmin())throw new BusinessException(ErrorCode.FORBIDDEN);}
}
