package com.hongjie.pms.AI;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class QuickTest {
    public static void main(String[] args) {
        String apiKey = System.getenv().getOrDefault("DASHSCOPE_API_KEY", "");

        ChatModel model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl("https://api.deepseek.com")
                .modelName("deepseek-chat")
                .build();

        String response = model.chat("你好，请介绍一下自己");
        System.out.println(response);
    }
}
