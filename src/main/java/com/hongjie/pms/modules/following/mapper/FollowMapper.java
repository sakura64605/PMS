package com.hongjie.pms.modules.following.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.following.entity.Follow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FollowMapper extends BaseMapper<Follow> {

    @Select("SELECT following_id FROM follow WHERE follower_id = #{followerId}")
    List<Long> getFollowingIds(@Param("followerId") Long followerId);

    @Select("SELECT COUNT(*) FROM follow WHERE following_id = #{userId}")
    int countFans(@Param("userId") Long userId);

    @Select("SELECT follower_id FROM follow WHERE following_id = #{followingId} LIMIT #{offset}, #{limit}")
    List<Long> selectFollowersPage(@Param("followingId") Long followingId,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

}
