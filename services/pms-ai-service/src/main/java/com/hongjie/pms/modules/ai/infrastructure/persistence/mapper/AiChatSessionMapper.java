package com.hongjie.pms.modules.ai.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.ai.infrastructure.persistence.entity.AiChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiChatSessionMapper extends BaseMapper<AiChatSession> {

    @Select("SELECT * FROM ai_chat_session WHERE session_id = #{sessionId}")
    AiChatSession selectBySessionId(@Param("sessionId") String sessionId);
}
