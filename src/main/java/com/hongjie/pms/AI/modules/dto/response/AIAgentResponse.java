package com.hongjie.pms.AI.modules.dto.response;

import com.hongjie.pms.AI.modules.dto.ToolCall;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIAgentResponse {
    private String messageId;
    private String sessionId;
    private String content;
    private String answer;
    private List<ToolCall> toolCalls;
    private Boolean needHuman;
    private List<String> suggestions;
    private Integer tokensUsed;
    private Integer latencyMs;
}