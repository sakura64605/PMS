package com.hongjie.pms.modules.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.activity.entity.Activity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {

    @Update("UPDATE activity SET like_count = like_count + 1 WHERE id = #{activityId}")
    void incrementLikeCount(@Param("activityId") Long activityId);

    @Update("UPDATE activity SET like_count = like_count - 1 WHERE id = #{activityId} AND like_count > 0")
    void decrementLikeCount(@Param("activityId") Long activityId);

    @Update("UPDATE activity SET comment_count = comment_count + 1 WHERE id = #{activityId}")
    void incrementCommentCount(@Param("activityId") Long activityId);
}
