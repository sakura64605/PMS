package com.hongjie.pms.modules.analytics.application;

import com.hongjie.pms.platform.enums.ErrorCode;
import com.hongjie.pms.platform.exception.BusinessException;
import java.time.*;
import java.time.temporal.ChronoUnit;

public final class StatisticsDates {
    private StatisticsDates() {}

    public static void date(LocalDate date) {
        if (date == null || date.getYear() < 1000 || date.getYear() > 9998)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "统计日期无效");
    }

    public static int range(LocalDate start, LocalDate end, int max) {
        date(start);
        date(end);
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days < 1 || days > max)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "日期范围应为1至" + max + "天");
        return Math.toIntExact(days);
    }

    public static YearMonth month(String value) {
        try {
            YearMonth month = YearMonth.parse(value);
            date(month.atDay(1));
            return month;
        } catch (RuntimeException invalid) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "月份格式应为yyyy-MM");
        }
    }
}
