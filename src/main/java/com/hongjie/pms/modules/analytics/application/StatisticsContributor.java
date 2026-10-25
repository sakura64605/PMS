package com.hongjie.pms.modules.analytics.application;
import com.hongjie.pms.modules.analytics.infrastructure.persistence.entity.DailyStatistics;
import java.time.LocalDateTime;
public interface StatisticsContributor {
    String name();
    void collect(DailyStatistics result,LocalDateTime start,LocalDateTime end);
}
