package com.hongjie.pms.AI.common.config;

import com.hongjie.pms.AI.common.TokenUsageTracker;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AIAgentConfiguration {

    private final AIAgentConfig aiAgentConfig;
    private final TokenUsageTracker tokenUsageTracker;

    @Bean
    public ChatModel chatLanguageModel() {
        log.info("初始化 ChatModel, model={}, temperature={}, maxTokens={}",
                aiAgentConfig.getModelName(), aiAgentConfig.getTemperature(), aiAgentConfig.getMaxTokens());

        return OpenAiChatModel.builder()
                .apiKey(aiAgentConfig.getApiKey())
                .baseUrl(aiAgentConfig.getBaseUrl())
                .modelName(aiAgentConfig.getModelName())
                .temperature(aiAgentConfig.getTemperature())
                .maxTokens(aiAgentConfig.getMaxTokens())
                .timeout(Duration.ofSeconds(aiAgentConfig.getTimeout()))
                .logRequests(true)
                .logResponses(true)
                .listeners(createTokenUsageListener())
                .build();
    }

    /**
     * 每次 LLM 调用响应时，把该次 TokenUsage 累加到当前请求的累积器（ReAct 循环多次调用都会计到）。
     */
    private ChatModelListener createTokenUsageListener() {
        return new ChatModelListener() {
            @Override
            public void onResponse(ChatModelResponseContext context) {
                TokenUsageTracker.TokenUsageAccumulator acc = tokenUsageTracker.current();
                if (acc == null || context.chatResponse() == null) {
                    return;
                }
                acc.add(context.chatResponse().tokenUsage());
                // 该响应若带工具调用请求，属于"工具决策轮"；计数用于判断 ReAct 是否多轮迭代
                boolean isToolRound = context.chatResponse().aiMessage() != null
                        && context.chatResponse().aiMessage().hasToolExecutionRequests();
                acc.recordCall(isToolRound);
            }
        };
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("初始化 EmbeddingModel（BGE-small-zh-v1.5，中文，512维，供 Qdrant 向量存储使用）...");
        return new BgeSmallZhV15EmbeddingModel();
    }
}