package com.hongjie.pms.AI.modules.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.AI.modules.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {
    
    @Update("UPDATE ai_chat_message SET feedback = #{feedback} WHERE message_id = #{messageId}")
    void updateFeedback(@Param("messageId") String messageId, @Param("feedback") Integer feedback);
}