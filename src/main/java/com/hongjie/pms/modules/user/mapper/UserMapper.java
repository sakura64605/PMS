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
}
