package com.hongjie.pms.modules.privateMessage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongjie.pms.modules.privateMessage.entity.PrivateConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PrivateConversationMapper extends BaseMapper<PrivateConversation> {

    @Update("UPDATE private_conversation SET unread_count_a = unread_count_a + 1 WHERE id = #{conversationId} AND user_a = #{userId}")
    void incrementUnreadA(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    @Update("UPDATE private_conversation SET unread_count_b = unread_count_b + 1 WHERE id = #{conversationId} AND user_b = #{userId}")
    void incrementUnreadB(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    @Update("UPDATE private_conversation SET unread_count_a = 0 WHERE id = #{conversationId} AND user_a = #{userId}")
    void clearUnreadA(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    @Update("UPDATE private_conversation SET unread_count_b = 0 WHERE id = #{conversationId} AND user_b = #{userId}")
    void clearUnreadB(@Param("conversationId") Long conversationId, @Param("userId") Long userId);
}