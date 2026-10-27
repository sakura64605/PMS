package com.hongjie.pms.modules.analytics.application;

import com.hongjie.pms.modules.analytics.infrastructure.persistence.entity.DailyStatistics;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
public class StatisticsCollector {
    private final List<StatisticsContributor> contributors;

    public StatisticsCollector(List<StatisticsContributor> contributors) {
        Set<String> names = new HashSet<>();
        for (var contributor : contributors)
            if (!names.add(contributor.name()))
                throw new IllegalStateException(
                        "Duplicate statistics group: " + contributor.name());
        this.contributors = List.copyOf(contributors);
    }

    public DailyStatistics collect(LocalDate date, LocalDateTime end) {
        DailyStatistics row = new DailyStatistics();
        row.setStatDate(date);
        for (var contributor : contributors) contributor.collect(row, date.atStartOfDay(), end);
        return row;
    }
}
