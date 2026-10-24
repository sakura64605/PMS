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

    @Update("UPDATE activity SET current_people = current_people + 1 WHERE id = #{activityId}")
    int incrementCurrentPeople(Long activityId);

    @Update("UPDATE activity SET current_people = current_people - 1 WHERE id = #{id}")
    void decrementCurrentPeople(Long id);
    /** Conditional transitions recheck live data after waiting for an editor's row lock. */
    @Update("UPDATE activity SET status=1,update_time=#{now} WHERE id=#{id} AND status=0 AND audit_status=1 AND deleted=0 AND start_time<=#{now} AND end_time>#{now}")
    int startIfDue(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);

    @Update("UPDATE activity SET status=2,update_time=#{now} WHERE id=#{id} AND status=1 AND deleted=0 AND end_time<=#{now}")
    int finishIfDue(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);
    @Update("UPDATE activity SET status=2,update_time=#{now} WHERE id=#{id} AND status IN (0,1,2) AND deleted=0 AND audit_status=1 AND end_time<=#{now}")
    int finishForStatisticsIfDue(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);
}
