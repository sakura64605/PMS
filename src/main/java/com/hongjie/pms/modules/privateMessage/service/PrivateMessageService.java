package com.hongjie.pms.modules.privateMessage.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hongjie.pms.modules.privateMessage.dto.ConversationDto;
import com.hongjie.pms.modules.privateMessage.dto.MessageDto;
import com.hongjie.pms.modules.privateMessage.dto.SendMessageRequest;

public interface PrivateMessageService {

    /**
     * 发送私信
     */
    MessageDto sendMessage(Long fromUserId, SendMessageRequest request);

    /**
     * 获取会话列表
     */
    IPage<ConversationDto> getConversationList(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 获取聊天记录
     */
    IPage<MessageDto> getMessageList(Long userId, Long conversationId, Integer pageNum, Integer pageSize);

    /**
     * 获取未读消息总数
     */
    int getTotalUnreadCount(Long userId);

    /**
     * 标记会话已读
     */
    void markConversationRead(Long userId, Long conversationId);

    /**
     * 获取或创建会话（不发送消息）
     */
    ConversationDto getOrCreateConversation(Long userId, Long targetUserId);

    /**
     * 删除会话（删除整个会话及所有消息）
     */
    void deleteConversation(Long userId, Long conversationId);

    /**
     * 清空聊天记录（保留会话，只删除消息）
     */
    void clearMessages(Long userId, Long conversationId);
}