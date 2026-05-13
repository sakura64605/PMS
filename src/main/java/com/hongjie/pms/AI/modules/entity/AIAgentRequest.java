package com.hongjie.pms.AI.modules.entity;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class AIAgentRequest {
    @NotBlank(message = "会话ID不能为空")
    private String sessionId;
    
    @NotBlank(message = "消息内容不能为空")
    private String message;
    
    private Long userId;
    
    private Boolean stream = false;
    
    private List<ChatHistory> history;
}

@Data
class ChatHistory {
    private String role;
    private String content;
}