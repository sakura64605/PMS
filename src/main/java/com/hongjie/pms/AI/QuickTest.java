package com.hongjie.pms.AI;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.message.AiMessage;

public class QuickTest {
    public static void main(String[] args) {
        String apiKey = "CHANGE_API_KEY";

        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .modelName("qwen-turbo")
                .build();

        // 方式1：使用 generate 方法
        Response<AiMessage> response = model.generate(UserMessage.from("你好，请介绍一下自己"));
        System.out.println(response.content().text());
        
        // 方式2：直接传入 String（更简单）
        // String response = model.generate("你好，请介绍一下自己");
        // System.out.println(response);
    }
}