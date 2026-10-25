package com.hongjie.pms.modules.community.following.application;

import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import org.apache.ibatis.annotations.*;

/** Current reads and atomic counters; callers hold both user rows in ascending ID order. */
@Mapper
public interface FollowWriteMapper {
    @Select("SELECT id,user_name,status FROM user WHERE id=#{id} FOR UPDATE")
    User lockUser(@Param("id") Long id);

    @Select("SELECT id FROM follow WHERE follower_id=#{actor} AND following_id=#{target} FOR UPDATE")
    Long relation(@Param("actor") Long actor,@Param("target") Long target);

    @Insert("INSERT INTO follow(follower_id,following_id,create_time) VALUES(#{actor},#{target},NOW())")
    int insert(@Param("actor") Long actor,@Param("target") Long target);

    @Delete("DELETE FROM follow WHERE id=#{id} AND follower_id=#{actor} AND following_id=#{target}")
    int delete(@Param("id") Long id,@Param("actor") Long actor,@Param("target") Long target);

    @Update("UPDATE user SET following_count=GREATEST(0,COALESCE(following_count,0)+#{delta}) WHERE id=#{id}")
    int followingCount(@Param("id") Long id,@Param("delta") int delta);

    @Update("UPDATE user SET follower_count=GREATEST(0,COALESCE(follower_count,0)+#{delta}) WHERE id=#{id}")
    int followerCount(@Param("id") Long id,@Param("delta") int delta);
}
