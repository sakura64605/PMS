package com.hongjie.pms.AI.common.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class AIAgentConfiguration {

    // API Key 从配置类读取，也可以直接写
    private static final String API_KEY = "CHANGE_API_KEY";

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("初始化 ChatLanguageModel...");

        return OpenAiChatModel.builder()
                .apiKey(API_KEY)
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .modelName("qwen-turbo")
                .temperature(0.7)
                .maxTokens(2048)
                .timeout(Duration.ofSeconds(30))
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