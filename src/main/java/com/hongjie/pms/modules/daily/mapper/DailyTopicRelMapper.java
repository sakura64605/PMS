package com.hongjie.pms.modules.daily.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.daily.entity.DailyTopicRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DailyTopicRelMapper extends BaseMapper<DailyTopicRel> {

    @Select("SELECT topic_id FROM daily_topic_rel WHERE daily_id = #{dailyId}")
    List<Long> getTopicIdsByDailyId(@Param("dailyId") Long dailyId);

    @Select("SELECT daily_id FROM daily_topic_rel WHERE topic_id = #{topicId} ORDER BY create_time DESC LIMIT #{limit}")
    List<Long> getDailyIdsByTopicId(@Param("topicId") Long topicId, @Param("limit") int limit);
}