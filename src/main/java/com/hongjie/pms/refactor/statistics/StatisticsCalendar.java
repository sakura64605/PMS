package com.hongjie.pms.refactor.statistics;
import org.springframework.stereotype.Component;
import java.time.*;
@Component
public class StatisticsCalendar {
    public LocalDate today(){return LocalDate.now(ZoneId.of("Asia/Shanghai"));}
    public LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));}
}
