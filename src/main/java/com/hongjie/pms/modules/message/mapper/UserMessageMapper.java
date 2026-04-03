package com.hongjie.pms.modules.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.message.entity.UserMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessage> {
    
    @Update("UPDATE user_message SET is_read = 1, read_time = NOW() WHERE id = #{id}")
    void markAsRead(Long id);
    
    @Update("UPDATE user_message SET is_read = 1, read_time = NOW() WHERE user_id = #{userId} AND type = #{type}")
    void markAllAsRead(Long userId, String type);
}