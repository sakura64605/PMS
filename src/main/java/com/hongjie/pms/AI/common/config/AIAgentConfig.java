package com.hongjie.pms.AI.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.agent")
public class AIAgentConfig {
    /** API Key */
    private String apiKey = "CHANGE_API_KEY";

    /** 模型名称 - 通义千问可选：qwen-turbo, qwen-plus, qwen-max */
    private String modelName = "qwen-turbo";

    /** 温度参数 */
    private double temperature = 0.7;

    /** 最大token */
    private int maxTokens = 2048;

    /** 超时时间（秒） */
    private int timeout = 30;

    /** 是否启用记忆 */
    private boolean enableMemory = true;

    /** 最大记忆轮数 */
    private int maxMemoryRounds = 10;

    /** 是否启用RAG */
    private boolean enableRag = true;

    /** RAG召回数量 */
    private int ragRecallCount = 3;
}

