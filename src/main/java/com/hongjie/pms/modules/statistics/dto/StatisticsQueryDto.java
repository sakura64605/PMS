package com.hongjie.pms.modules.statistics.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class StatisticsQueryDto {
    
    private LocalDate startDate;    // 开始日期
    private LocalDate endDate;      // 结束日期
    private String period;          // day/week/month/year
}