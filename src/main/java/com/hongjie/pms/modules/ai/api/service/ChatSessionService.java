package com.hongjie.pms.modules.ai.api.service;
import com.hongjie.pms.modules.ai.infrastructure.persistence.entity.AiChatSession;
import com.hongjie.pms.modules.ai.infrastructure.persistence.entity.AiChatMessage;
import java.util.List;
/** Runtime contract implemented by the migrated service. */
public interface ChatSessionService {
    String createSession(Long userId);
    List<AiChatSession> getUserSessions(Long userId);
    List<AiChatMessage> getSessionHistory(String sessionId);
    void deleteSession(String sessionId, Long userId);
    void clearMemory(String sessionId);
    void submitFeedback(String messageId, Integer score);
    boolean checkSessionOwner(String sessionId, Long userId);
}
