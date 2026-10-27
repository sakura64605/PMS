package com.hongjie.pms.modules.analytics.application;

import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.UserInfo;
import com.hongjie.pms.platform.exception.BusinessException;
import com.hongjie.pms.modules.analytics.infrastructure.persistence.entity.DailyStatistics;
import com.hongjie.pms.modules.analytics.infrastructure.persistence.mapper.DailyStatisticsMapper;
import com.hongjie.pms.modules.analytics.api.service.StatisticsService;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatisticsQueriesTest {
    DailyStatisticsMapper rows = mock(DailyStatisticsMapper.class);
    StatisticsFactMapper facts = mock(StatisticsFactMapper.class);
    StatisticsCollector collector = mock(StatisticsCollector.class);
    StatisticsCalendar calendar = mock(StatisticsCalendar.class);
    StatisticsQueries queries =
            new StatisticsQueries(rows, facts, collector, calendar, new StatisticsAssembler());

    @BeforeEach
    void setup() {
        UserInfo user = new UserInfo();
        user.setRole(1);
        UserContext.setUser(user);
        when(calendar.today()).thenReturn(LocalDate.of(2026, 10, 25));
        when(calendar.now()).thenReturn(LocalDateTime.of(2026, 10, 25, 12, 0));
        when(rows.selectByDateRange(any(), any())).thenReturn(List.of());
        when(collector.collect(any(), any()))
                .thenAnswer(
                        inv -> {
                            DailyStatistics row = new DailyStatistics();
                            row.setStatDate(inv.getArgument(0));
                            row.setDau(9);
                            return row;
                        });
    }

    @AfterEach
    void clean() {
        UserContext.clear();
    }

    @Test
    void missingDaysRetainCalendarBoundsAndZeros() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        DailyStatistics row = new DailyStatistics();
        row.setStatDate(start.plusDays(1));
        row.setDau(6);
        when(rows.selectByDateRange(start, start.plusDays(2))).thenReturn(List.of(row));
        var result = queries.range(start, start.plusDays(2));
        assertThat(result.getStartDate()).isEqualTo(start);
        assertThat(result.getEndDate()).isEqualTo(start.plusDays(2));
        assertThat(result.getAvgActiveUsers()).isEqualTo(2);
        assertThat(result.getMinDau()).isZero();
        assertThat(result.getTrendData().getDauList()).containsExactly(0, 6, 0);
        assertThat(result.getTotalNewUsers()).isZero();
    }

    @Test
    void todayAlwaysUsesFreshCollectorInsteadOfPersistedSnapshot() {
        LocalDate date = calendar.today();
        DailyStatistics stale = new DailyStatistics();
        stale.setStatDate(date);
        stale.setDau(1);
        when(rows.selectByDateRange(any(), any())).thenReturn(List.of(stale));
        var result = queries.daily(date);
        assertThat(result.getDau()).isEqualTo(9);
        assertThat(result.getTrendData().getDates()).hasSize(30);
        verify(collector).collect(date, calendar.now());
    }

    @Test
    void weeklyUsesMondaySundayAndSevenPoints() {
        var result = queries.weekly(LocalDate.of(2026, 9, 2));
        assertThat(result.getWeekRange()).isEqualTo("2026-08-31 ~ 2026-09-06");
        assertThat(result.getTrendData().getDates()).hasSize(7);
    }

    @Test
    void monthlyAndLeapYearIncludeEveryCalendarDay() {
        assertThat(queries.monthly("2024-02").getTrendData().getDates()).hasSize(29);
        assertThat(queries.yearly(2024).getTrendData().getDates()).hasSize(366);
    }

    @Test
    void percentageGrowthHandlesZeroAndDeclines() {
        var a = new StatisticsAssembler();
        assertThat(a.growth(0, 0)).isZero();
        assertThat(a.growth(3, 0)).isEqualTo(100);
        assertThat(a.growth(5, 10)).isEqualTo(-50);
    }

    @Test
    void invalidRangesAndFormatsAreRejected() {
        assertThatThrownBy(() -> queries.monthly("bad")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> queries.yearly(0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> queries.range(LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 1)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> queries.range(LocalDate.of(2000, 1, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void allQueriesRequireAdmin() {
        UserContext.clear();
        assertThatThrownBy(() -> queries.realtime()).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> queries.daily(calendar.today()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> queries.weekly(calendar.today()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> queries.monthly("2026-10")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> queries.yearly(2026)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> queries.range(calendar.today(), calendar.today()))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(rows, facts, collector);
    }

    @Test
    void facadeOverridesEveryLegacyPublicBusinessMethod() throws Exception {
        for (var method : StatisticsService.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                assertThat(
                                PmsStatisticsService.class.getDeclaredMethod(
                                        method.getName(), method.getParameterTypes()))
                        .isNotNull();
        }
    }

    @Test
    void duplicateContributorsFailFast() {
        StatisticsContributor a = mock(StatisticsContributor.class),
                b = mock(StatisticsContributor.class);
        when(a.name()).thenReturn("same");
        when(b.name()).thenReturn("same");
        assertThatThrownBy(() -> new StatisticsCollector(List.of(a, b)))
                .isInstanceOf(IllegalStateException.class);
    }
}
