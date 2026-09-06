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
    /** 本次请求触发的 LLM 调用总次数（≥3 说明 ReAct 多轮迭代，看到工具结果后又决策过） */
    private Integer llmCallCount;
    /** 其中"工具决策轮"次数 */
    private Integer toolRoundCount;
}