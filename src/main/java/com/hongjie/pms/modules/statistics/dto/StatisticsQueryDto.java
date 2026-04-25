package com.hongjie.pms.modules.statistics.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class StatisticsQueryDto {

    // 统计类型：daily/weekly/monthly/yearly/range
    private String type;

    // 日报/周报专用：查询日期
    private LocalDate date;

    // 月报专用：月份 (格式：2024-01)
    private String month;

    // 年报专用：年份
    private Integer year;

    // 自定义范围专用：开始日期
    private LocalDate startDate;

    // 自定义范围专用：结束日期
    private LocalDate endDate;
}