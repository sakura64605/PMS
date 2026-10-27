package com.hongjie.pms.modules.ai.api.service;

import lombok.NoArgsConstructor;
import java.util.List;

/** Runtime contract implemented by the migrated service. */
public interface ChatMemoryService {
    void saveMessage(String sessionId, String role, String content, Long userId);

    void saveMessage(
            String sessionId, String role, String content, Long userId, Integer tokensUsed);

    void saveMessage(
            String sessionId,
            String role,
            String content,
            Long userId,
            Integer tokensUsed,
            String toolCallsJson);

    List<MemoryMessage> getRecentMessages(String sessionId, int maxRounds);

    void clearMemory(String sessionId);

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class MemoryMessage {
        private String role;
        private String content;
        private long timestamp;
    }
}
