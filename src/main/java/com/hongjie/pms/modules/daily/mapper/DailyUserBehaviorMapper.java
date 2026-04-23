package com.hongjie.pms.modules.daily.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.daily.entity.DailyUserBehavior;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface DailyUserBehaviorMapper extends BaseMapper<DailyUserBehavior> {

    @Select("SELECT target_id FROM daily_user_behavior " +
            "WHERE user_id = #{userId} AND action_type IN ('like', 'share') " +
            "ORDER BY action_time DESC LIMIT #{limit}")
    List<Long> getLikedDailyIds(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("SELECT topic_id FROM daily_topic_rel WHERE daily_id IN " +
            "(SELECT target_id FROM daily_user_behavior WHERE user_id = #{userId} AND action_type = 'like')")
    List<Long> getLikedTopicIds(@Param("userId") Long userId);

    @Select("SELECT topic_id, COUNT(*) as cnt FROM daily_topic_rel " +
            "WHERE daily_id IN (SELECT target_id FROM daily_user_behavior WHERE user_id = #{userId}) " +
            "GROUP BY topic_id ORDER BY cnt DESC LIMIT #{limit}")
    List<Map<String, Object>> getUserTopicPreference(@Param("userId") Long userId, @Param("limit") int limit);
}