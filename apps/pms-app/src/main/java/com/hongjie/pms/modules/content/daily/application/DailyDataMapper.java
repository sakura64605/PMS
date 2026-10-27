package com.hongjie.pms.modules.content.daily.application;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface DailyDataMapper {
    @Select(
            "SELECT id FROM daily_post WHERE status=1 AND audit_status=1 ORDER BY COALESCE(like_count,0) DESC,COALESCE(view_count,0) DESC,id DESC LIMIT #{limit}")
    List<Long> hot(int limit);

    @Select(
            "SELECT id FROM daily_post WHERE status=1 AND audit_status=1 ORDER BY create_time DESC,id DESC LIMIT #{limit}")
    List<Long> fresh(int limit);

    @Select(
            "SELECT id FROM daily_post WHERE status=1 AND audit_status=1 AND user_id IN (SELECT following_id FROM follow WHERE follower_id=#{user}) ORDER BY create_time DESC,id DESC LIMIT #{limit}")
    List<Long> followed(@Param("user") Long user, @Param("limit") int limit);

    @Select({
        "<script>",
        "SELECT DISTINCT p.id,p.create_time FROM daily_post p JOIN daily_topic_rel r ON r.daily_id=p.id WHERE p.status=1 AND p.audit_status=1 AND r.topic_id IN",
        "<foreach collection='topics' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
        "ORDER BY p.create_time DESC,p.id DESC LIMIT #{limit}",
        "</script>"
    })
    List<Long> interested(@Param("topics") List<Long> topics, @Param("limit") int limit);

    @Update(
            "UPDATE daily_post SET view_count=COALESCE(view_count,0)+1 WHERE id=#{id} AND status=1 AND audit_status=1")
    int view(Long id);

    @Update(
            "UPDATE daily_post SET like_count=COALESCE(like_count,0)+1 WHERE id=#{id} AND status=1 AND audit_status=1")
    int like(Long id);

    @Update(
            "UPDATE daily_post SET like_count=GREATEST(COALESCE(like_count,0)-1,0) WHERE id=#{id} AND status=1 AND audit_status=1")
    int unlike(Long id);

    @Update(
            "UPDATE topic SET post_count=GREATEST(COALESCE(post_count,0)+#{delta},0) WHERE id=#{id}")
    int topicCount(@Param("id") Long id, @Param("delta") int delta);
}
