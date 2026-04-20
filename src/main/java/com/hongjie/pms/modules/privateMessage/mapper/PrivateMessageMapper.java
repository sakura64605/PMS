package com.hongjie.pms.modules.privateMessage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.privateMessage.entity.PrivateMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PrivateMessageMapper extends BaseMapper<PrivateMessage> {

    @Select("SELECT * FROM private_message WHERE conversation_id = #{conversationId} ORDER BY create_time DESC LIMIT #{offset}, #{limit}")
    List<PrivateMessage> selectByConversationId(@Param("conversationId") Long conversationId,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    @Update("UPDATE private_message SET is_read = 1 WHERE conversation_id = #{conversationId} AND to_user_id = #{userId} AND is_read = 0")
    int markAsRead(@Param("conversationId") Long conversationId, @Param("userId") Long userId);
}