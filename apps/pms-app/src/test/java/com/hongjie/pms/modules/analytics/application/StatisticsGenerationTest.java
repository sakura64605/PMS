package com.hongjie.pms.modules.analytics.application;

import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.UserInfo;
import com.hongjie.pms.modules.analytics.infrastructure.persistence.entity.DailyStatistics;
import com.hongjie.pms.modules.analytics.infrastructure.persistence.mapper.DailyStatisticsMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatisticsGenerationTest {
    StatisticsDayWriter writer = mock(StatisticsDayWriter.class);
    StatisticsFactMapper facts = mock(StatisticsFactMapper.class);
    DailyStatisticsMapper rows = mock(DailyStatisticsMapper.class);
    StatisticsCalendar calendar = mock(StatisticsCalendar.class);
    StatisticsGeneration generation = new StatisticsGeneration(writer, facts, rows, calendar);
    LocalDate today = LocalDate.of(2026, 10, 25), start = today.minusDays(3);

    @BeforeEach
    void setup() {
        when(calendar.today()).thenReturn(today);
        when(facts.earliestBusinessDate()).thenReturn(start);
        when(rows.selectByDateRange(any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
    }

    @Test
    void emptySnapshotsBootstrapAndInteriorHolesAreFilled() {
        DailyStatistics existing = new DailyStatistics();
        existing.setStatDate(start.plusDays(1));
        when(rows.selectByDateRange(start, today.minusDays(1))).thenReturn(List.of(existing));
        generation.compensate();
        verify(writer).write(start, false);
        verify(writer).write(start.plusDays(2), false);
        verify(writer, never()).write(existing.getStatDate(), false);
    }

    @Test
    void boundedBatchesContinueOnNextRun() {
        ReflectionTestUtils.setField(generation, "batch", 1);
        generation.compensate();
        verify(writer).write(start, false);
        verifyNoMoreInteractions(writer);
    }

    @Test
    void failureDoesNotSkipLaterDatesAndIsReported() {
        doThrow(new IllegalStateException("injected failure")).when(writer).write(start, false);
        assertThatThrownBy(generation::compensate).isInstanceOf(IllegalStateException.class);
        verify(writer).write(start.plusDays(1), false);
        verify(writer).write(start.plusDays(2), false);
    }

    @Test
    void dailyOnlyGeneratesYesterday() {
        generation.daily();
        verify(writer).write(today.minusDays(1), false);
    }

    @Test
    void manualRebuildRequiresAdminAndCompletedDates() {
        assertThatThrownBy(() -> generation.regenerate(start)).isInstanceOf(RuntimeException.class);
        UserInfo user = new UserInfo();
        user.setRole(1);
        UserContext.setUser(user);
        assertThatThrownBy(() -> generation.regenerateRange(start, today))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(writer);
        generation.regenerateRange(start, start.plusDays(1));
        verify(writer).write(start, true);
        verify(writer).write(start.plusDays(1), true);
    }
}
