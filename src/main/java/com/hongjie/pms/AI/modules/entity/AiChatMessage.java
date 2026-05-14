package com.hongjie.pms.AI.modules.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_chat_message")
public class AiChatMessage {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String sessionId;
    
    private String messageId;
    
    private String role;
    
    private String content;
    
    private String toolCalls;
    
    private String toolCallId;
    
    private Integer tokensUsed;
    
    private Integer latencyMs;
    
    private Integer feedback;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}