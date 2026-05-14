package com.hongjie.pms.AI.agent;

import com.hongjie.pms.AI.modules.dto.ToolCall;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIAgentEngine {

    private final ChatLanguageModel chatLanguageModel;
    private final ChatMemoryService memoryService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final SearchPetsTool searchPetsTool;
    private final SearchActivitiesTool searchActivitiesTool;

    public AIAgentResponse process(AIAgentRequest request) {
        long startTime = System.currentTimeMillis();
        String messageId = UUID.randomUUID().toString();
        String userMessage = request.getMessage();

        try {
            memoryService.saveMessage(request.getSessionId(), "user", userMessage, request.getUserId());

            // 检查是否需要转人工
            if (shouldTransferToHuman(userMessage)) {
                return AIAgentResponse.builder()
                        .messageId(messageId)
                        .sessionId(request.getSessionId())
                        .content("我暂时无法处理这个问题，正在为您转接人工客服...")
                        .needHuman(true)
                        .build();
            }

            // ==================== 核心：意图识别 + 工具调用 ====================
            String toolResult = null;
            String toolName = null;

            // 判断用户意图，调用对应工具
            if (isSearchPetsIntent(userMessage)) {
                log.info("识别为宠物搜索意图，调用 SearchPetsTool");
                Map<String, Object> args = buildPetSearchArgs(userMessage);
                ToolCall result = searchPetsTool.execute(args, request.getUserId());
                toolResult = result.getResult();
                toolName = "搜索宠物";
            }
            else if (isSearchActivitiesIntent(userMessage)) {
                log.info("识别为活动搜索意图，调用 SearchActivitiesTool");
                Map<String, Object> args = buildActivitySearchArgs(userMessage);
                ToolCall result = searchActivitiesTool.execute(args, request.getUserId());
                toolResult = result.getResult();
                toolName = "搜索活动";
            }

            // 根据是否有工具结果，生成不同回答
            String answer;
            if (toolResult != null) {
                // 有工具调用结果，让 AI 基于真实数据回答
                answer = generateAnswerWithToolResult(userMessage, toolResult, toolName);
            } else {
                // 普通对话，使用 RAG + 大模型
                String systemPrompt = buildSystemPrompt();
                String relevantKnowledge = knowledgeBaseService != null ?
                        knowledgeBaseService.searchRelevant(userMessage) : null;
                String userMessageWithContext = buildUserMessage(userMessage, relevantKnowledge);

                Response<dev.langchain4j.data.message.AiMessage> response = chatLanguageModel.generate(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userMessageWithContext)
                );
                answer = response.content().text();
            }

            // 保存助手回复
            memoryService.saveMessage(request.getSessionId(), "assistant", answer, request.getUserId());

            long latency = System.currentTimeMillis() - startTime;

            return AIAgentResponse.builder()
                    .messageId(messageId)
                    .sessionId(request.getSessionId())
                    .content(answer)
                    .answer(answer)
                    .needHuman(false)
                    .suggestions(getSuggestionsByIntent(userMessage))
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

    /**
     * 判断是否为宠物搜索意图
     */
    private boolean isSearchPetsIntent(String message) {
        String[] keywords = {"领养", "宠物", "猫", "狗", "猫咪", "狗狗", "救助", "找宠物", "看宠物", "小动物"};
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为活动搜索意图
     */
    private boolean isSearchActivitiesIntent(String message) {
        String[] keywords = {"活动", "报名", "线下", "聚会", "讲座", "义诊"};
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建宠物搜索参数
     */
    private Map<String, Object> buildPetSearchArgs(String message) {
        Map<String, Object> args = new HashMap<>();

        // 提取关键词
        String keyword = extractKeyword(message);
        args.put("keyword", keyword);

        // 识别宠物类型
        if (message.contains("猫") || message.contains("猫咪")) {
            args.put("title", "猫");
            args.put("content", "猫");
        } else if (message.contains("狗") || message.contains("狗狗")) {
            args.put("title", "狗");
            args.put("content", "狗");
        }

        args.put("limit", 5);
        return args;
    }

    /**
     * 构建活动搜索参数
     */
    private Map<String, Object> buildActivitySearchArgs(String message) {
        Map<String, Object> args = new HashMap<>();

        String keyword = extractKeyword(message);
        args.put("keyword", keyword);

        // 提取地点（简单实现）
        String location = extractLocation(message);
        if (location != null) {
            args.put("location", location);
        }

        args.put("limit", 5);
        return args;
    }

    /**
     * 提取关键词
     */
    private String extractKeyword(String message) {
        // 移除常见前缀
        String cleaned = message.replaceAll("(我想|我要|帮我|搜索|查找|查询|看看|有没有|推荐|有什么|介绍下)", "");
        // 限制长度
        if (cleaned.length() > 20) {
            cleaned = cleaned.substring(0, 20);
        }
        return cleaned.trim();
    }

    /**
     * 提取地点
     */
    private String extractLocation(String message) {
        // 简单匹配城市名
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([\\u4e00-\\u9fa5]{2,3}?[市区县])");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 基于工具查询结果生成回答
     */
    private String generateAnswerWithToolResult(String userMessage, String toolResult, String toolName) {
        String prompt = String.format("""
        用户问：%s
        
        我刚刚调用了【%s】工具，从数据库中查询到的真实结果是：
        %s
        
        请根据这个查询结果，用热情、友好的语气回复用户。
        
        要求：
        1. 如果结果中有宠物/活动，用自然语言列出关键信息，让用户感受到真实的数据
        2. 使用合适的emoji增加亲和力（🐱🐶🎉📅📍等）
        3. 在回复末尾，可以询问用户是否需要进一步了解某个具体项目
        4. 如果结果为空，委婉告知用户没找到，并给出建议
        
        回复格式要自然流畅，不要直接输出JSON或原始数据。
        """, userMessage, toolName, toolResult);

        return chatLanguageModel.generate(prompt);
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

    private List<String> getSuggestionsByIntent(String message) {
        if (isSearchPetsIntent(message)) {
            return List.of("领养需要什么条件？", "如何发布领养信息？", "查看更多宠物");
        }
        if (isSearchActivitiesIntent(message)) {
            return List.of("怎么报名活动？", "活动在哪里参加？", "有哪些活动推荐？");
        }
        return List.of("如何发布领养信息？", "平台有哪些活动？", "联系人工客服");
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