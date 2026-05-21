package com.hongjie.pms.AI.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.AI.modules.dto.ToolCall;
import com.hongjie.pms.AI.modules.dto.request.AIAgentRequest;
import com.hongjie.pms.AI.modules.dto.response.AIAgentResponse;
import com.hongjie.pms.AI.modules.entity.AiKnowledgeBase;
import com.hongjie.pms.AI.modules.mapper.AiKnowledgeBaseMapper;
import com.hongjie.pms.AI.modules.service.ChatMemoryService;
import com.hongjie.pms.AI.rag.KnowledgeBaseService;
import com.hongjie.pms.AI.tool.ToolRegistry;
import com.hongjie.pms.modules.activity.entity.ActivitySignup;
import com.hongjie.pms.modules.activity.mapper.ActivitySignupMapper;
import com.hongjie.pms.modules.petpost.entity.PetPost;
import com.hongjie.pms.modules.petpost.mapper.PetPostMapper;
import com.hongjie.pms.modules.user.entity.User;
import com.hongjie.pms.modules.user.mapper.UserMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIAgentEngine {

    private final ChatLanguageModel chatLanguageModel;
    private final ChatMemoryService memoryService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ToolRegistry toolRegistry;
    private final UserMapper userMapper;
    private final PetPostMapper petPostMapper;
    private final ActivitySignupMapper signupMapper;
    private final AiKnowledgeBaseMapper knowledgeBaseMapper;

    public AIAgentResponse process(AIAgentRequest request) {
        long startTime = System.currentTimeMillis();
        String messageId = UUID.randomUUID().toString();
        String userMessage = request.getMessage();
        Long userId = request.getUserId();

        try {
            if (shouldTransferToHuman(userMessage)) {
                memoryService.saveMessage(request.getSessionId(), "user", userMessage, userId);
                return AIAgentResponse.builder()
                        .messageId(messageId)
                        .sessionId(request.getSessionId())
                        .content("正在为您转接人工客服，请稍候...")
                        .needHuman(true)
                        .build();
            }

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(buildSystemPrompt(userId, userMessage)));

            List<ChatMemoryService.MemoryMessage> history = memoryService.getRecentMessages(
                    request.getSessionId(), 5);
            for (ChatMemoryService.MemoryMessage msg : history) {
                if ("user".equals(msg.getRole())) {
                    messages.add(UserMessage.from(msg.getContent()));
                } else if ("assistant".equals(msg.getRole())) {
                    messages.add(AiMessage.from(msg.getContent()));
                }
            }
            messages.add(UserMessage.from(userMessage));

            memoryService.saveMessage(request.getSessionId(), "user", userMessage, userId);

            String answer = generateWithTools(messages, userId);

            memoryService.saveMessage(request.getSessionId(), "assistant", answer, userId);

            long latency = System.currentTimeMillis() - startTime;

            return AIAgentResponse.builder()
                    .messageId(messageId)
                    .sessionId(request.getSessionId())
                    .content(answer)
                    .answer(answer)
                    .needHuman(false)
                    .suggestions(smartSuggestions(userMessage))
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

    private String generateWithTools(List<ChatMessage> messages, Long userId) {
        List<ToolSpecification> toolSpecs = toolRegistry.getToolSpecifications();
        log.debug("发送消息到LLM，可用工具: {}", toolSpecs.stream().map(ToolSpecification::name).toList());

        Response<AiMessage> response = chatLanguageModel.generate(messages, toolSpecs);
        AiMessage aiMessage = response.content();

        if (aiMessage.hasToolExecutionRequests()) {
            List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
            log.info("LLM请求调用工具: {}",
                    toolRequests.stream().map(r -> r.name() + "(" + r.arguments() + ")").toList());

            messages.add(aiMessage);

            for (ToolExecutionRequest toolReq : toolRequests) {
                ToolCall result = toolRegistry.execute(toolReq.name(), toolReq.arguments(), userId);
                log.debug("工具 {} 返回 {} 字符", toolReq.name(),
                        result.getResult() != null ? result.getResult().length() : 0);
                messages.add(ToolExecutionResultMessage.from(toolReq, result.getResult()));
            }

            Response<AiMessage> finalResponse = chatLanguageModel.generate(messages);
            return finalResponse.content().text();
        }

        return aiMessage.text();
    }

    private String buildSystemPrompt(Long userId, String userMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            你是宠物社区平台"宠小伴"的AI助手，帮用户解决平台使用问题、查询信息、提供建议。

            核心能力：
            - 搜索宠物领养/救助信息（search_pets）
            - 查询活动信息（search_activities）
            - 查看用户自己的帖子和报名（my_pets、my_activities）
            - 浏览宠友日记和热门话题（search_daily_posts、hot_topics）
            - 查看系统通知（my_notifications）

            重要规则：
            1. 热情亲切，适当使用emoji
            2. 用户问到"我的帖子""我发布的""我报名的"时，主动调用对应工具
            3. 基于工具返回的真实数据回答，不要编造信息
            4. 不确定的事情建议联系人工客服
            """);

        if (userId != null) {
            try {
                User user = userMapper.selectById(userId);
                if (user != null) {
                    long postCount = petPostMapper.selectCount(
                            new LambdaQueryWrapper<PetPost>().eq(PetPost::getUserId, userId));
                    long signupCount = signupMapper.selectCount(
                            new LambdaQueryWrapper<ActivitySignup>()
                                    .eq(ActivitySignup::getUserId, userId)
                                    .eq(ActivitySignup::getStatus, 1));

                    sb.append("\n当前用户画像：");
                    sb.append("\n- 昵称：").append(user.getNickName() != null ? user.getNickName() : user.getUserName());
                    sb.append("\n- 发布的领养帖：").append(postCount).append("个");
                    sb.append("\n- 已报名的活动：").append(signupCount).append("个");
                    if (user.getSignature() != null && !user.getSignature().isEmpty()) {
                        sb.append("\n- 签名：").append(user.getSignature());
                    }
                }
            } catch (Exception e) {
                log.warn("获取用户画像失败: userId={}", userId, e);
            }
        }

        // 直接从数据库搜索知识库（不依赖向量模型），操作步骤类问题从这里获取
        if (userMessage != null) {
            try {
                log.info("正在搜索知识库: {}", userMessage);
                LambdaQueryWrapper<AiKnowledgeBase> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(AiKnowledgeBase::getStatus, 1);
                wrapper.and(w -> {
                    // 中文没有空格分隔，这里将查询拆成所有可能的2字词组做OR匹配
                    // 例如"怎么报名活动" → "怎么" OR "报名" OR "活动" OR "么报" OR "名活"
                    Set<String> keywords = new LinkedHashSet<>();
                    // 添加完整查询词
                    if (userMessage.trim().length() >= 2) {
                        keywords.add(userMessage.trim());
                    }
                    // 按常见分隔符切分
                    for (String part : userMessage.split("[\\s,，、。.?!？!：:；;]+")) {
                        if (part.trim().length() >= 2) {
                            keywords.add(part.trim());
                        }
                        // 生成所有2字组合（中文核心词通常是2字）
                        for (int i = 0; i + 1 < part.length(); i++) {
                            String bigram = part.substring(i, Math.min(i + 2, part.length()));
                            if (bigram.length() >= 2) {
                                keywords.add(bigram);
                            }
                        }
                    }
                    // 过滤掉无意义的单字和常用虚词
                    keywords.removeIf(kw -> kw.length() < 2 || "怎么".equals(kw) || "如何".equals(kw) || "怎样".equals(kw) || "哪个".equals(kw) || "哪些".equals(kw));

                    log.info("知识库搜索关键词: {}", keywords);
                    boolean first = true;
                    for (String kw : keywords) {
                        if (first) {
                            w.like(AiKnowledgeBase::getTitle, kw)
                                    .or()
                                    .like(AiKnowledgeBase::getContent, kw);
                            first = false;
                        } else {
                            w.or(i -> i.like(AiKnowledgeBase::getTitle, kw)
                                    .or()
                                    .like(AiKnowledgeBase::getContent, kw));
                        }
                    }
                });
                wrapper.last("LIMIT 3");

                List<AiKnowledgeBase> knowledges = knowledgeBaseMapper.selectList(wrapper);
                log.info("知识库搜索到 {} 条结果", knowledges.size());

                if (!knowledges.isEmpty()) {
                    sb.append("\n\n以下是从平台知识库中检索到的相关信息，请优先据此回答，不要凭自己知识编造：\n");
                    for (AiKnowledgeBase k : knowledges) {
                        sb.append("【").append(k.getTitle()).append("】\n");
                        sb.append(k.getContent()).append("\n\n");
                    }
                }
            } catch (Exception e) {
                log.warn("知识库直接查询失败", e);
            }
        }

        sb.append("\n\n当前时间：").append(LocalDateTime.now().toString());
        return sb.toString();
    }

    private boolean shouldTransferToHuman(String message) {
        String[] keywords = {"人工客服", "转人工", "投诉", "举报"};
        for (String kw : keywords) {
            if (message.contains(kw)) return true;
        }
        return false;
    }

    private List<String> smartSuggestions(String message) {
        if (message.contains("宠物") || message.contains("领养") || message.contains("猫") || message.contains("狗")) {
            return List.of("领养需要什么条件？", "如何发布领养信息？", "帮我看看我的帖子");
        }
        if (message.contains("活动") || message.contains("报名")) {
            return List.of("怎么报名活动？", "我报名了哪些活动？", "最近有什么新活动？");
        }
        if (message.contains("日记") || message.contains("话题") || message.contains("动态")) {
            return List.of("最近有什么热门话题？", "看看猫咪日常", "怎么发日记？");
        }
        return List.of("最近有什么热门话题？", "平台有哪些活动？", "如何发布领养信息？", "查看我的帖子");
    }
}