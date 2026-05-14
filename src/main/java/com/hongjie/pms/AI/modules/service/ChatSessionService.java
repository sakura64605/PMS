package com.hongjie.pms.AI.modules.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.AI.modules.entity.AiChatSession;
import com.hongjie.pms.AI.modules.entity.AiChatMessage;
import com.hongjie.pms.AI.modules.mapper.AiChatMessageMapper;
import com.hongjie.pms.AI.modules.mapper.AiChatSessionMapper;
import com.hongjie.pms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final ChatMemoryService memoryService;

    public String createSession(Long userId) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");

        // 生成会话标题：使用时间戳
        String title = "新对话 " + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"));

        AiChatSession session = new AiChatSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTitle(title);
        session.setStatus(1);
        session.setMessageCount(0);
        session.setSource("web");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());

        sessionMapper.insert(session);
        log.info("创建AI会话: sessionId={}, userId={}", sessionId, userId);

        return sessionId;
    }

    public List<AiChatSession> getUserSessions(Long userId) {
        LambdaQueryWrapper<AiChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChatSession::getUserId, userId)
                .eq(AiChatSession::getStatus, 1)
                .orderByDesc(AiChatSession::getUpdatedAt);
        return sessionMapper.selectList(wrapper);
    }

    public List<AiChatMessage> getSessionHistory(String sessionId) {
        LambdaQueryWrapper<AiChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChatMessage::getSessionId, sessionId)
                .orderByAsc(AiChatMessage::getCreatedAt);
        return messageMapper.selectList(wrapper);
    }

    @Transactional
    public void deleteSession(String sessionId, Long userId) {
        // 验证会话属于当前用户
        AiChatSession session = sessionMapper.selectBySessionId(sessionId);
        if (session == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作");
        }

        // 软删除会话
        session.setStatus(0);
        sessionMapper.updateById(session);

        // 清理 Redis 缓存
        memoryService.clearMemory(sessionId);

        log.info("删除会话: sessionId={}, userId={}", sessionId, userId);
    }

    public void clearMemory(String sessionId) {
        memoryService.clearMemory(sessionId);
    }

    public void submitFeedback(String messageId, Integer score) {
        messageMapper.updateFeedback(messageId, score);
    }

    public boolean checkSessionOwner(String sessionId, Long userId) {
        AiChatSession session = sessionMapper.selectBySessionId(sessionId);
        if (session == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (session.getUserId().equals(userId)) {
            return true;
        }
        return false;
    }
}