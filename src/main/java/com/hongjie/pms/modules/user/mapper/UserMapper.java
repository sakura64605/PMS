package com.hongjie.pms.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Update("UPDATE user SET last_active_time = NOW() WHERE id = #{userId}")
    void updateLastActiveTime(@Param("userId") Long userId);

    @Update("UPDATE user SET follower_count = follower_count - 1 WHERE id = #{userId}")
    void decreaseFollowerCount(@Param("userId") Long userId);

    @Update("UPDATE user SET following_count = following_count - 1 WHERE id = #{userId}")
    void decreaseFollowingCount(Long currentUserId);

    @Update("UPDATE user SET follower_count = follower_count + 1 WHERE id = #{userId}")
    void increaseFollowerCount(Long userId);

    @Update("UPDATE user SET following_count = following_count + 1 WHERE id = #{userId}")
    void increaseFollowingCount(Long currentUserId);
}
