package com.hongjie.pms.refactor.statistics;
import org.apache.ibatis.annotations.*;
import java.time.*;
@Mapper
public interface StatisticsFactMapper {
    @SelectProvider(type=StatisticsSql.class,method="aggregate")
    StatisticsFacts aggregate(@Param("source") StatisticsSource source,@Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,@Param("week") LocalDateTime week,@Param("month") LocalDateTime month);
    @SelectProvider(type=StatisticsSql.class,method="earliest") LocalDate earliestBusinessDate();
    @Select("SELECT COUNT(*) FROM user WHERE create_time<#{end} AND last_active_time>=#{start} AND last_active_time<#{end}")
    int active(@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);
    @Insert("INSERT INTO daily_statistics(stat_date,create_time,update_time) VALUES(#{date},NULL,NULL) ON DUPLICATE KEY UPDATE id=id") int ensureDate(LocalDate date);
    @Select("SELECT * FROM daily_statistics WHERE stat_date=#{date} FOR UPDATE")
    com.hongjie.pms.modules.statistics.entity.DailyStatistics lockDate(LocalDate date);
    @Select("SELECT MIN(stat_date) FROM daily_statistics") LocalDate earliestSnapshot();
}
