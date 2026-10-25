package com.hongjie.pms.refactor.statistics;
import com.hongjie.pms.modules.statistics.entity.DailyStatistics;
import java.time.LocalDateTime;
public interface StatisticsContributor {
    String name();
    void collect(DailyStatistics result,LocalDateTime start,LocalDateTime end);
}
