package com.hongjie.pms.modules.analytics.application;
import com.hongjie.pms.modules.analytics.infrastructure.persistence.mapper.DailyStatisticsMapper;
import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @RequiredArgsConstructor
public class StatisticsDayWriter {
    private final StatisticsFactMapper facts;
    private final DailyStatisticsMapper rows;
    private final StatisticsCollector collector;
    private final StatisticsCalendar calendar;
    private final StatisticsCache cache;
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public boolean write(java.time.LocalDate date,boolean force){
        StatisticsDates.date(date);
        if(!date.isBefore(calendar.today()))throw new BusinessException(ErrorCode.PARAM_ERROR,"只能重建已结束日期，请使用实时统计查看今日");
        // A duplicate-key no-op acquires an exclusive lock directly, avoiding shared-lock upgrade deadlocks.
        facts.ensureDate(date);
        var existing=facts.lockDate(date);
        if(existing==null)throw new IllegalStateException("Missing statistics date lock");
        if(existing.getUpdateTime()!=null&&!force)return false;
        var snapshot=collector.collect(date,date.plusDays(1).atStartOfDay());
        snapshot.setId(existing.getId());
        snapshot.setCreateTime(existing.getCreateTime()==null?calendar.now():existing.getCreateTime());
        snapshot.setUpdateTime(calendar.now());
        if(rows.updateById(snapshot)!=1)throw new IllegalStateException("Statistics snapshot update failed");
        cache.afterCommit();return true;
    }
}
