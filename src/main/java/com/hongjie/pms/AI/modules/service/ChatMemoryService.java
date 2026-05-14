package com.hongjie.pms.AI.modules.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.AI.modules.entity.AiChatMessage;
import com.hongjie.pms.AI.modules.mapper.AiChatMessageMapper;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMemoryService {
    
    private final AiChatMessageMapper messageMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String MEMORY_CACHE_PREFIX = "ai:memory:session:";
    
    public void saveMessage(String sessionId, String role, String content, Long userId) {
        String cacheKey = MEMORY_CACHE_PREFIX + sessionId;
        MemoryMessage msg = new MemoryMessage(role, content, System.currentTimeMillis());
        redisTemplate.opsForList().rightPush(cacheKey, msg);
        redisTemplate.expire(cacheKey, 24, TimeUnit.HOURS);
        
        AiChatMessage entity = new AiChatMessage();
        entity.setSessionId(sessionId);
        entity.setMessageId(UUID.randomUUID().toString());
        entity.setRole(role);
        entity.setContent(content);
        entity.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(entity);
        
        log.debug("保存消息: sessionId={}, role={}", sessionId, role);
    }
    
    public List<MemoryMessage> getRecentMessages(String sessionId, int maxRounds) {
        String cacheKey = MEMORY_CACHE_PREFIX + sessionId;
        List<Object> rawList = redisTemplate.opsForList().range(cacheKey, -maxRounds * 2, -1);
        
        List<MemoryMessage> messages = new ArrayList<>();
        if (rawList != null && !rawList.isEmpty()) {
            for (Object obj : rawList) {
                if (obj instanceof MemoryMessage) {
                    messages.add((MemoryMessage) obj);
                }
            }
        }
        
        if (messages.isEmpty()) {
            LambdaQueryWrapper<AiChatMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AiChatMessage::getSessionId, sessionId)
                    .orderByDesc(AiChatMessage::getCreatedAt)
                    .last("LIMIT " + (maxRounds * 2));
            
            List<AiChatMessage> dbMessages = messageMapper.selectList(wrapper);
            for (int i = dbMessages.size() - 1; i >= 0; i--) {
                AiChatMessage msg = dbMessages.get(i);
                messages.add(new MemoryMessage(msg.getRole(), msg.getContent(), 
                        msg.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli()));
            }
        }
        
        return messages;
    }
    
    public void clearMemory(String sessionId) {
        String cacheKey = MEMORY_CACHE_PREFIX + sessionId;
        redisTemplate.delete(cacheKey);
    }
    
    @lombok.Data
    @NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MemoryMessage {
        private String role;
        private String content;
        private long timestamp;
    }
}