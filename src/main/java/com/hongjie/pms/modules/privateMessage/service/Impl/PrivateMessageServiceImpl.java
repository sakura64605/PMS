package com.hongjie.pms.modules.privateMessage.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.enums.ErrorCode;
import com.hongjie.pms.common.exception.BusinessException;
import com.hongjie.pms.modules.message.websocket.WebSocketHandler;
import com.hongjie.pms.modules.privateMessage.dto.ConversationDto;
import com.hongjie.pms.modules.privateMessage.dto.MessageDto;
import com.hongjie.pms.modules.privateMessage.dto.SendMessageRequest;
import com.hongjie.pms.modules.privateMessage.entity.PrivateConversation;
import com.hongjie.pms.modules.privateMessage.entity.PrivateMessage;
import com.hongjie.pms.modules.privateMessage.mapper.PrivateConversationMapper;
import com.hongjie.pms.modules.privateMessage.mapper.PrivateMessageMapper;
import com.hongjie.pms.modules.privateMessage.service.PrivateMessageService;
import com.hongjie.pms.modules.user.dto.UserSimpleDto;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrivateMessageServiceImpl implements PrivateMessageService {

    private final PrivateConversationMapper conversationMapper;
    private final PrivateMessageMapper messageMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public MessageDto sendMessage(Long fromUserId, SendMessageRequest request) {
        Long toUserId = request.getToUserId();

        if (fromUserId.equals(toUserId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能给自己发私信");
        }

        // 1. 获取或创建会话
        PrivateConversation conversation = getOrCreateConversationOnly(fromUserId, toUserId);

        // 2. 保存消息
        PrivateMessage message = new PrivateMessage();
        message.setConversationId(conversation.getId());
        message.setFromUserId(fromUserId);
        message.setToUserId(toUserId);
        message.setMessageType(request.getMessageType());
        message.setContent(request.getContent());
        message.setIsRead(0);
        messageMapper.insert(message);

        // 3. 更新会话
        conversation.setLastMessage(request.getContent());
        conversation.setLastMessageTime(LocalDateTime.now());

        // 增加接收者的未读数
        if (conversation.getUserA().equals(toUserId)) {
            conversation.setUnreadCountA(conversation.getUnreadCountA() + 1);
        } else {
            conversation.setUnreadCountB(conversation.getUnreadCountB() + 1);
        }
        conversationMapper.updateById(conversation);

        // 4. WebSocket 实时推送
        User fromUser = userMapper.selectById(fromUserId);
        MessageDto messageDto = convertToDto(message, fromUser);
        WebSocketHandler.pushToUser(toUserId, messageDto);

        log.info("发送私信: from={}, to={}, content={}", fromUserId, toUserId, request.getContent());
        return messageDto;
    }

    @Override
    public IPage<ConversationDto> getConversationList(Long userId, Integer pageNum, Integer pageSize) {
        Page<PrivateConversation> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<PrivateConversation> wrapper = new LambdaQueryWrapper<PrivateConversation>()
                .and(w -> w.eq(PrivateConversation::getUserA, userId).or().eq(PrivateConversation::getUserB, userId))
                .orderByDesc(PrivateConversation::getLastMessageTime);
        IPage<PrivateConversation> conversationPage = conversationMapper.selectPage(page, wrapper);

        List<ConversationDto> records = conversationPage.getRecords().stream()
                .map(conv -> convertToConversationDto(conv, userId))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Page<ConversationDto> resultPage = new Page<>(pageNum, pageSize, conversationPage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    /**
     * 获取聊天记录（过滤已删除的）
     */
    @Override
    public IPage<MessageDto> getMessageList(Long userId, Long conversationId, Integer pageNum, Integer pageSize) {
        // 1. 验证权限
        PrivateConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || (!conversation.getUserA().equals(userId) && !conversation.getUserB().equals(userId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该会话");
        }

        // 2. 标记消息已读
        markConversationRead(userId, conversationId);

        // 3. 查询未删除的消息
        // 当前用户看不到的消息：
        //    - 自己发出的且自己删除的 (is_deleted_by_from = 1)
        //    - 对方发出的且自己删除的 (is_deleted_by_to = 1)
        LambdaQueryWrapper<PrivateMessage> wrapper = new LambdaQueryWrapper<PrivateMessage>()
                .eq(PrivateMessage::getConversationId, conversationId)
                .and(w -> w
                        // 自己发出的：没有被自己删除
                        .eq(PrivateMessage::getFromUserId, userId)
                        .eq(PrivateMessage::getIsDeletedByFrom, 0)
                        .or()
                        // 对方发出的：没有被自己删除
                        .eq(PrivateMessage::getToUserId, userId)
                        .eq(PrivateMessage::getIsDeletedByTo, 0)
                )
                .orderByAsc(PrivateMessage::getCreateTime);

        Page<PrivateMessage> page = new Page<>(pageNum, pageSize);
        IPage<PrivateMessage> messagePage = messageMapper.selectPage(page, wrapper);

        // 4. 转换并返回
        List<MessageDto> records = messagePage.getRecords().stream()
                .map(msg -> convertToDto(msg, null))
                .collect(Collectors.toList());

        Page<MessageDto> resultPage = new Page<>(pageNum, pageSize, messagePage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    @Override
    public int getTotalUnreadCount(Long userId) {
        LambdaQueryWrapper<PrivateConversation> wrapper = new LambdaQueryWrapper<PrivateConversation>()
                .and(w -> w.eq(PrivateConversation::getUserA, userId).gt(PrivateConversation::getUnreadCountA, 0)
                        .or()
                        .eq(PrivateConversation::getUserB, userId).gt(PrivateConversation::getUnreadCountB, 0));
        List<PrivateConversation> conversations = conversationMapper.selectList(wrapper);

        int total = 0;
        for (PrivateConversation conv : conversations) {
            if (conv.getUserA().equals(userId)) {
                total += conv.getUnreadCountA();
            } else {
                total += conv.getUnreadCountB();
            }
        }
        return total;
    }

    @Override
    @Transactional
    public void markConversationRead(Long userId, Long conversationId) {
        PrivateConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            return;
        }

        if (conversation.getUserA().equals(userId) && conversation.getUnreadCountA() > 0) {
            conversationMapper.clearUnreadA(conversationId, userId);
            messageMapper.markAsRead(conversationId, userId);
        } else if (conversation.getUserB().equals(userId) && conversation.getUnreadCountB() > 0) {
            conversationMapper.clearUnreadB(conversationId, userId);
            messageMapper.markAsRead(conversationId, userId);
        }
    }

    @Override
    public ConversationDto getOrCreateConversation(Long userId, Long otherUserId) {
        if (userId.equals(otherUserId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能和自己聊天");
        }

        // 获取或创建会话
        PrivateConversation conversation = getOrCreateConversationOnly(userId, otherUserId);

        // 返回会话信息
        return convertToConversationDto(conversation, userId);
    }

    /**
     * 仅获取或创建会话（不发送消息）
     */
    private PrivateConversation getOrCreateConversationOnly(Long userA, Long userB) {
        LambdaQueryWrapper<PrivateConversation> wrapper = new LambdaQueryWrapper<PrivateConversation>()
                .and(w -> w.eq(PrivateConversation::getUserA, userA).eq(PrivateConversation::getUserB, userB))
                .or(w -> w.eq(PrivateConversation::getUserA, userB).eq(PrivateConversation::getUserB, userA));
        PrivateConversation conversation = conversationMapper.selectOne(wrapper);

        if (conversation == null) {
            conversation = new PrivateConversation();
            conversation.setUserA(userA);
            conversation.setUserB(userB);
            conversation.setUnreadCountA(0);
            conversation.setUnreadCountB(0);
            conversationMapper.insert(conversation);
            log.info("创建新会话: userA={}, userB={}", userA, userB);
        }

        return conversation;
    }

    @Override
    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        PrivateConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }

        // 标记该用户的所有消息为已删除（软删除）
        if (conversation.getUserA().equals(userId)) {
            // 用户A删除：标记所有 from_user_id = A 或 to_user_id = A 的消息
            LambdaQueryWrapper<PrivateMessage> wrapper = new LambdaQueryWrapper<PrivateMessage>()
                    .eq(PrivateMessage::getConversationId, conversationId)
                    .and(w -> w.eq(PrivateMessage::getFromUserId, userId).or().eq(PrivateMessage::getToUserId, userId));

            List<PrivateMessage> messages = messageMapper.selectList(wrapper);
            for (PrivateMessage msg : messages) {
                if (msg.getFromUserId().equals(userId)) {
                    msg.setIsDeletedByFrom(1);
                }
                if (msg.getToUserId().equals(userId)) {
                    msg.setIsDeletedByTo(1);
                }
                messageMapper.updateById(msg);
            }
        } else if (conversation.getUserB().equals(userId)) {
            LambdaQueryWrapper<PrivateMessage> wrapper = new LambdaQueryWrapper<PrivateMessage>()
                    .eq(PrivateMessage::getConversationId, conversationId)
                    .and(w -> w.eq(PrivateMessage::getFromUserId, userId).or().eq(PrivateMessage::getToUserId, userId));

            List<PrivateMessage> messages = messageMapper.selectList(wrapper);
            for (PrivateMessage msg : messages) {
                if (msg.getFromUserId().equals(userId)) {
                    msg.setIsDeletedByFrom(1);
                }
                if (msg.getToUserId().equals(userId)) {
                    msg.setIsDeletedByTo(1);
                }
                messageMapper.updateById(msg);
            }
        } else {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作");
        }

        // 检查双方是否都删除了该会话的所有消息，如果是则删除会话
        checkAndDeleteConversationIfBothDeleted(conversationId);

        log.info("用户删除会话: userId={}, conversationId={}", userId, conversationId);
    }

    @Override
    @Transactional
    public void clearMessages(Long userId, Long conversationId) {
        PrivateConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }

        // 清空该用户的所有消息（软删除）
        if (conversation.getUserA().equals(userId)) {
            LambdaQueryWrapper<PrivateMessage> wrapper = new LambdaQueryWrapper<PrivateMessage>()
                    .eq(PrivateMessage::getConversationId, conversationId)
                    .and(w -> w.eq(PrivateMessage::getFromUserId, userId).or().eq(PrivateMessage::getToUserId, userId));

            List<PrivateMessage> messages = messageMapper.selectList(wrapper);
            for (PrivateMessage msg : messages) {
                if (msg.getFromUserId().equals(userId)) {
                    msg.setIsDeletedByFrom(1);
                }
                if (msg.getToUserId().equals(userId)) {
                    msg.setIsDeletedByTo(1);
                }
                messageMapper.updateById(msg);
            }
        } else if (conversation.getUserB().equals(userId)) {
            LambdaQueryWrapper<PrivateMessage> wrapper = new LambdaQueryWrapper<PrivateMessage>()
                    .eq(PrivateMessage::getConversationId, conversationId)
                    .and(w -> w.eq(PrivateMessage::getFromUserId, userId).or().eq(PrivateMessage::getToUserId, userId));

            List<PrivateMessage> messages = messageMapper.selectList(wrapper);
            for (PrivateMessage msg : messages) {
                if (msg.getFromUserId().equals(userId)) {
                    msg.setIsDeletedByFrom(1);
                }
                if (msg.getToUserId().equals(userId)) {
                    msg.setIsDeletedByTo(1);
                }
                messageMapper.updateById(msg);
            }
        }

        // 更新会话的最后消息（如果该用户清空了，最后消息应该不显示给自己）
        if (userId.equals(conversation.getUserA())) {
            // 重新计算该用户的最后一条可见消息
            LambdaQueryWrapper<PrivateMessage> wrapper = new LambdaQueryWrapper<PrivateMessage>()
                    .eq(PrivateMessage::getConversationId, conversationId)
                    .eq(PrivateMessage::getIsDeletedByTo, 0)
                    .eq(PrivateMessage::getIsDeletedByFrom, 0)
                    .orderByDesc(PrivateMessage::getCreateTime)
                    .last("LIMIT 1");
            PrivateMessage lastMsg = messageMapper.selectOne(wrapper);

            conversation.setLastMessage(lastMsg != null ? lastMsg.getContent() : null);
            conversation.setLastMessageTime(lastMsg != null ? lastMsg.getCreateTime() : null);
            conversationMapper.updateById(conversation);
        }

        log.info("用户清空聊天记录: userId={}, conversationId={}", userId, conversationId);
    }

    /**
     * 检查双方是否都删除了会话的所有消息，如果是则删除会话
     */
    private void checkAndDeleteConversationIfBothDeleted(Long conversationId) {
        PrivateConversation conversation = conversationMapper.selectById(conversationId);

        // 查询该会话下所有未被双方删除的消息
        LambdaQueryWrapper<PrivateMessage> wrapper = new LambdaQueryWrapper<PrivateMessage>()
                .eq(PrivateMessage::getConversationId, conversationId)
                .eq(PrivateMessage::getIsDeletedByFrom, 0)
                .eq(PrivateMessage::getIsDeletedByTo, 0);
        Long count = messageMapper.selectCount(wrapper);

        // 如果没有未被删除的消息，删除会话
        if (count == 0) {
            clearMessages(UserContext.getUserId(), conversationId);
            conversationMapper.deleteById(conversationId);
            log.info("双方都已删除，会话已删除: conversationId={}", conversationId);
        }
    }


    private ConversationDto convertToConversationDto(PrivateConversation conversation, Long currentUserId) {
        Long otherUserId = conversation.getUserA().equals(currentUserId) ? conversation.getUserB() : conversation.getUserA();
        User otherUser = userMapper.selectById(otherUserId);
        if (otherUser == null) {
            return null;
        }

        int unreadCount = conversation.getUserA().equals(currentUserId)
                ? conversation.getUnreadCountA()
                : conversation.getUnreadCountB();

        return ConversationDto.builder()
                .conversationId(conversation.getId())
                .otherUser(UserSimpleDto.builder()
                        .userId(otherUser.getId())
                        .username(otherUser.getUserName())
                        .nickname(otherUser.getNickName())
                        .avatar(otherUser.getAvatar())
                        .build())
                .lastMessage(conversation.getLastMessage())
                .lastMessageTime(conversation.getLastMessageTime())
                .unreadCount(unreadCount)
                .build();
    }

    private MessageDto convertToDto(PrivateMessage message, User fromUser) {
        if (fromUser == null) {
            fromUser = userMapper.selectById(message.getFromUserId());
        }
        return MessageDto.builder()
                .id(message.getId())
                .fromUserId(message.getFromUserId())
                .fromUserName(fromUser != null ? fromUser.getUserName() : null)
                .fromUserAvatar(fromUser != null ? fromUser.getAvatar() : null)
                .toUserId(message.getToUserId())
                .messageType(message.getMessageType())
                .content(message.getContent())
                .isRead(message.getIsRead())
                .createTime(message.getCreateTime())
                .build();
    }
}