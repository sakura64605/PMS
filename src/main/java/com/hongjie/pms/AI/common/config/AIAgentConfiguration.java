package com.hongjie.pms.AI.common.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
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

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("初始化 ChatLanguageModel, model={}, temperature={}, maxTokens={}",
                aiAgentConfig.getModelName(), aiAgentConfig.getTemperature(), aiAgentConfig.getMaxTokens());

        return OpenAiChatModel.builder()
                .apiKey(aiAgentConfig.getApiKey())
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .modelName(aiAgentConfig.getModelName())
                .temperature(aiAgentConfig.getTemperature())
                .maxTokens(aiAgentConfig.getMaxTokens())
                .timeout(Duration.ofSeconds(aiAgentConfig.getTimeout()))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("初始化 EmbeddingModel...");
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    public InMemoryEmbeddingStore embeddingStore() {
        log.info("初始化 EmbeddingStore...");
        return new InMemoryEmbeddingStore();
    }
}