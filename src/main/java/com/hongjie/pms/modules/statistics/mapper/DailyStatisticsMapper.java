package com.hongjie.pms.modules.statistics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.statistics.entity.DailyStatistics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DailyStatisticsMapper extends BaseMapper<DailyStatistics> {
    
    @Select("SELECT * FROM daily_statistics WHERE stat_date BETWEEN #{startDate} AND #{endDate} ORDER BY stat_date ASC")
    List<DailyStatistics> selectByDateRange(@Param("startDate") LocalDate startDate, 
                                             @Param("endDate") LocalDate endDate);
    
    @Select("SELECT * FROM daily_statistics WHERE stat_date = #{date}")
    DailyStatistics selectByDate(@Param("date") LocalDate date);
    
    @Select("SELECT * FROM daily_statistics ORDER BY stat_date DESC LIMIT 7")
    List<DailyStatistics> selectLast7Days();
    
    @Select("SELECT * FROM daily_statistics ORDER BY stat_date DESC LIMIT 30")
    List<DailyStatistics> selectLast30Days();
}