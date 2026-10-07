package com.hongjie.pms.AI.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.agent")
public class AIAgentConfig {
    /** API Key — 必须在 application.yaml 中配置 ai.agent.api-key */
    private String apiKey;

    /** OpenAI 兼容接口 baseUrl —— DeepSeek: https://api.deepseek.com */
    private String baseUrl = "https://api.deepseek.com";

    /** 模型名称 - DeepSeek: deepseek-chat（支持 function calling）；阿里云 qwen-turbo/qwen-plus/qwen-max */
    private String modelName = "deepseek-chat";

    /** 温度参数 */
    private double temperature = 0.7;

    /** 最大token */
    private int maxTokens = 2048;

    /** 超时时间（秒） */
    private int timeout = 30;

    /** 是否启用短期记忆（滑动窗口，注入最近 N 轮） */
    private boolean enableMemory = true;

    /** 短期记忆滑动窗口：注入的最近对话轮数 */
    private int maxMemoryRounds = 10;

    /** 是否启用长期记忆（对话中抽取用户持久事实/偏好，跨会话注入） */
    private boolean enableLongTermMemory = false;

    /** 注入 system prompt 的长期记忆条数上限 */
    private int maxLongTermMemories = 20;

    /** 是否启用RAG */
    private boolean enableRag = true;

    /** RAG召回数量 */
    private int ragRecallCount = 3;
}

