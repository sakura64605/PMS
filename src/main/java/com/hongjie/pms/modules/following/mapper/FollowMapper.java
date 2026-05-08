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

    /**
     * 批量查询当前用户是否关注了多个用户
     * @param followerId 当前用户ID
     * @param followingIds 目标用户ID列表
     * @return 已关注的用户ID列表
     */
    @Select({
            "<script>",
            "SELECT following_id FROM follow WHERE follower_id = #{followerId} AND following_id IN",
            "<foreach collection='followingIds' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    List<Long> selectFollowedIds(@Param("followerId") Long followerId,
                                  @Param("followingIds") List<Long> followingIds);

}
