package com.hongjie.pms.AI.common.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AIAgentConfiguration {

    /**
     * 通义千问 Chat 模型（使用 OpenAI 兼容接口）
     * DashScope 兼容接口地址: https://dashscope.aliyuncs.com/compatible-mode/v1
     */
    @Bean
    public OpenAiChatModel openAiChatModel(AIAgentConfig config) {
        return OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .modelName(config.getModelName())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .timeout(Duration.ofSeconds(config.getTimeout()))
                .logRequests(true)   // 开发环境可开启日志
                .logResponses(true)
                .build();
    }

    /**
     * 本地向量化模型（用于 RAG）
     * 将文本转换为向量，用于相似度检索
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        // 使用轻量级本地向量模型（约 120MB）
        return new AllMiniLmL6V2EmbeddingModel();
    }

    /**
     * 本地向量存储（内存版）
     */
    @Bean
    public InMemoryEmbeddingStore embeddingStore() {
        return new InMemoryEmbeddingStore();
    }
}
