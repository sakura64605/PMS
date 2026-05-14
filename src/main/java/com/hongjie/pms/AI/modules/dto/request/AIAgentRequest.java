package com.hongjie.pms.AI.modules.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIAgentRequest {
    
    @NotBlank(message = "会话ID不能为空")
    private String sessionId;
    
    @NotBlank(message = "消息内容不能为空")
    private String message;
    
    private Long userId;
    
    private Boolean stream = false;
    
    private List<ChatHistory> history;
    
    @Data
    public static class ChatHistory {
        private String role;
        private String content;
    }
}