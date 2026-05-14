package com.hongjie.pms.AI.modules.service;

import com.hongjie.pms.AI.modules.entity.AiChatSession;
import com.hongjie.pms.AI.modules.entity.AiChatMessage;
import com.hongjie.pms.AI.modules.mapper.AiChatMessageMapper;
import com.hongjie.pms.AI.modules.mapper.AiChatSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
        
        AiChatSession session = new AiChatSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setStatus(1);
        session.setMessageCount(0);
        session.setSource("web");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        
        sessionMapper.insert(session);
        log.info("创建AI会话: sessionId={}, userId={}", sessionId, userId);
        
        return sessionId;
    }
    
    public List<AiChatMessage> getSessionHistory(String sessionId) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiChatMessage> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(AiChatMessage::getSessionId, sessionId)
                .orderByAsc(AiChatMessage::getCreatedAt);
        return messageMapper.selectList(wrapper);
    }
    
    public void clearMemory(String sessionId) {
        memoryService.clearMemory(sessionId);
    }
    
    public void submitFeedback(String messageId, Integer score) {
        messageMapper.updateFeedback(messageId, score);
    }
}