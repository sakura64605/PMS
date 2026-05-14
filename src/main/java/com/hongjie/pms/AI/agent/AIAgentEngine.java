package com.hongjie.pms.AI.agent;

import com.hongjie.pms.AI.modules.dto.request.AIAgentRequest;
import com.hongjie.pms.AI.modules.dto.response.AIAgentResponse;
import com.hongjie.pms.AI.modules.service.ChatMemoryService;
import com.hongjie.pms.AI.rag.KnowledgeBaseService;
import com.hongjie.pms.AI.tool.SearchActivitiesTool;
import com.hongjie.pms.AI.tool.SearchPetsTool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIAgentEngine {

    // 注入 ChatLanguageModel 接口
    private final ChatLanguageModel chatLanguageModel;
    private final ChatMemoryService memoryService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final SearchPetsTool searchPetsTool;      // 注入宠物搜索工具
    private final SearchActivitiesTool searchActivitiesTool;  // 注入活动搜索工具

    public AIAgentResponse process(AIAgentRequest request) {
        long startTime = System.currentTimeMillis();
        String messageId = UUID.randomUUID().toString();

        try {
            // 保存用户消息
            memoryService.saveMessage(request.getSessionId(), "user", request.getMessage(), request.getUserId());

            // 检查是否需要转人工
            if (shouldTransferToHuman(request.getMessage())) {
                return AIAgentResponse.builder()
                        .messageId(messageId)
                        .sessionId(request.getSessionId())
                        .content("我暂时无法处理这个问题，正在为您转接人工客服...")
                        .needHuman(true)
                        .build();
            }

            // 构建系统提示词
            String systemPrompt = buildSystemPrompt();

            // RAG 检索相关知识
            String relevantKnowledge = null;
            if (knowledgeBaseService != null) {
                relevantKnowledge = knowledgeBaseService.searchRelevant(request.getMessage());
            }

            // 构建用户消息（包含知识库上下文）
            String userMessageWithContext = buildUserMessage(request.getMessage(), relevantKnowledge);

            // 调用大模型
            Response<dev.langchain4j.data.message.AiMessage> response = chatLanguageModel.generate(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userMessageWithContext)
            );

            String answer = response.content().text();

            // 保存助手回复
            memoryService.saveMessage(request.getSessionId(), "assistant", answer, request.getUserId());

            long latency = System.currentTimeMillis() - startTime;

            return AIAgentResponse.builder()
                    .messageId(messageId)
                    .sessionId(request.getSessionId())
                    .content(answer)
                    .answer(answer)
                    .needHuman(false)
                    .suggestions(getDefaultSuggestions())
                    .tokensUsed(answer.length() / 2)
                    .latencyMs((int) latency)
                    .build();

        } catch (Exception e) {
            log.error("AI Agent处理失败: sessionId={}", request.getSessionId(), e);
            return AIAgentResponse.builder()
                    .messageId(messageId)
                    .sessionId(request.getSessionId())
                    .content("抱歉，我遇到了一些问题，请稍后再试或联系人工客服。")
                    .needHuman(true)
                    .build();
        }
    }

    private String buildSystemPrompt() {
        return """
            你是一个专业的宠物服务平台AI客服助手，名叫"宠小伴"。
            
            你可以帮助用户：查询宠物信息、查询活动信息、解答平台使用问题、引导用户操作。
            
            回答要求：
            1. 热情、专业、简洁
            2. 不了解的问题不要编造，建议联系人工客服
            3. 可以使用emoji让回复更生动
            
            当前时间：%s
            """.formatted(LocalDateTime.now().toString());
    }

    private String buildUserMessage(String userMessage, String relevantKnowledge) {
        if (relevantKnowledge != null && !relevantKnowledge.isEmpty()) {
            return "请参考以下知识库信息回答问题：\n\n" + relevantKnowledge + "\n\n用户问题：" + userMessage;
        }
        return userMessage;
    }

    private boolean shouldTransferToHuman(String message) {
        String[] keywords = {"人工客服", "转人工", "投诉", "举报"};
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getDefaultSuggestions() {
        return List.of(
                "如何发布领养信息？",
                "平台有哪些活动？",
                "怎么报名参加活动？",
                "如何联系人工客服？"
        );
    }
}