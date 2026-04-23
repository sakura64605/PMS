package com.hongjie.pms.modules.daily.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.daily.entity.DailyPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DailyPostMapper extends BaseMapper<DailyPost> {

    @Update("UPDATE daily_post SET view_count = view_count + 1 WHERE id = #{id}")
    void incrementViewCount(@Param("id") Long id);

    @Update("UPDATE daily_post SET like_count = like_count + 1 WHERE id = #{id}")
    void incrementLikeCount(@Param("id") Long id);

    @Update("UPDATE daily_post SET like_count = like_count - 1 WHERE id = #{id} AND like_count > 0")
    void decrementLikeCount(@Param("id") Long id);

    @Update("UPDATE daily_post SET comment_count = comment_count + 1 WHERE id = #{id}")
    void incrementCommentCount(@Param("id") Long id);

    @Select("SELECT * FROM daily_post WHERE audit_status = 1 AND status = 1 " +
            "ORDER BY like_count DESC, view_count DESC LIMIT #{limit}")
    List<DailyPost> getHotPosts(@Param("limit") int limit);

    @Select("SELECT * FROM daily_post WHERE audit_status = 1 AND status = 1 " +
            "AND id IN (SELECT daily_id FROM daily_topic_rel WHERE topic_id = #{topicId}) " +
            "ORDER BY create_time DESC LIMIT #{limit}")
    List<DailyPost> getPostsByTopic(@Param("topicId") Long topicId, @Param("limit") int limit);
}