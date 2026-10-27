package com.hongjie.pms.modules.analytics.application;

import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.UserInfo;
import com.hongjie.pms.modules.analytics.api.service.StatisticsService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatisticsFacadeTest {
    private <T> void collaborator(AnnotationConfigApplicationContext context, Class<T> type) {
        context.registerBean(type, () -> mock(type));
    }

    @Test
    void primaryFacadeRoutesAllExistingEntrypointsToNewComponents() {
        try (var context = new AnnotationConfigApplicationContext()) {
            for (Class<?> type :
                    PmsStatisticsService.class.getConstructors()[0].getParameterTypes())
                collaborator(context, type);
            context.register(PmsStatisticsService.class);
            context.refresh();
            StatisticsService service = context.getBean(StatisticsService.class);
            assertThat(service).isInstanceOf(PmsStatisticsService.class);
            var queries = context.getBean(StatisticsQueries.class);
            var generation = context.getBean(StatisticsGeneration.class);
            LocalDate date = LocalDate.of(2026, 10, 24);
            service.getDailyStatistics(date);
            service.getWeeklyStatistics(date);
            service.getMonthlyStatistics("2026-10");
            service.getYearlyStatistics(2026);
            service.getRangeStatistics(date, date);
            service.getRealtimeStatistics();
            service.generateDailyStatistics();
            service.compensateMissingStatistics();
            service.regenerateStatistics(date);
            service.regenerateStatisticsRange(date, date);
            assertThatThrownBy(service::clearAllStatisticsCache)
                    .isInstanceOf(RuntimeException.class);
            try {
                UserInfo admin = new UserInfo();
                admin.setRole(1);
                UserContext.setUser(admin);
                service.clearAllStatisticsCache();
            } finally {
                UserContext.clear();
            }
            verify(queries).daily(date);
            verify(queries).weekly(date);
            verify(queries).monthly("2026-10");
            verify(queries).yearly(2026);
            verify(queries).range(date, date);
            verify(queries).realtime();
            verify(generation).daily();
            verify(generation).compensate();
            verify(generation).regenerate(date);
            verify(generation).regenerateRange(date, date);
            verify(context.getBean(StatisticsCache.class)).afterCommit();
        }
    }
}
