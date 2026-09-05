package com.hongjie.pms.AI.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongjie.pms.AI.common.TokenUsageTracker;
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
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIAgentEngine {

    private final ChatModel chatLanguageModel;
    private final ChatMemoryService memoryService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final TokenUsageTracker tokenUsageTracker;
    private final ToolRegistry toolRegistry;
    private final UserMapper userMapper;
    private final PetPostMapper petPostMapper;
    private final ActivitySignupMapper signupMapper;
    private final AiKnowledgeBaseMapper knowledgeBaseMapper;

    /** ReAct 循环最大轮数（Thought→Action→Observation），防止死循环 */
    private static final int MAX_TOOL_CALLING_ROUND_TRIPS = 5;

    public AIAgentResponse process(AIAgentRequest request) {
        long startTime = System.currentTimeMillis();
        String messageId = UUID.randomUUID().toString();
        String userMessage = request.getMessage();
        Long userId = request.getUserId();
        // 记录用户消息是否已落库，AI 异常时 catch 里据此补齐用户消息与故障回复
        boolean userSaved = false;
        // 本次请求的 token 累积器：ChatModelListener 在每次 LLM 响应时累加，覆盖 ReAct 循环全部调用
        TokenUsageTracker.TokenUsageAccumulator usage = tokenUsageTracker.begin();

        try {
            if (shouldTransferToHuman(userMessage)) {
                memoryService.saveMessage(request.getSessionId(), "user", userMessage, userId);
                userSaved = true;
                String transferMsg = "正在为您转接人工客服，请稍候...";
                memoryService.saveMessage(request.getSessionId(), "assistant", transferMsg, userId);
                return AIAgentResponse.builder()
                        .messageId(messageId)
                        .sessionId(request.getSessionId())
                        .content(transferMsg)
                        .needHuman(true)
                        .tokensUsed(usage.totalTokens())
                        .build();
            }

            // 请求级收集：工具调用轨迹（返回给前端）
            List<ToolCall> toolTrace = new ArrayList<>();

            // 工具执行器（闭包捕获 userId；执行异常作为观察结果回喂模型纠错）
            Map<ToolSpecification, ToolExecutor> toolExecutors = toolRegistry.getToolExecutors(userId);

            // 每请求现搭 ReAct Agent：内置 Thought→Action→Observation 循环，无共享状态、线程安全
            UntypedAgent agent = AgenticServices.agentBuilder()
                    .chatModel(chatLanguageModel)
                    .tools(toolExecutors)
                    .systemMessageProvider(input -> buildSystemPrompt(userId, userMessage))
                    .userMessageProvider(input -> userMessage)
                    .chatMemoryProvider(memoryId -> buildSeededMemory(request.getSessionId()))
                    .maxToolCallingRoundTrips(MAX_TOOL_CALLING_ROUND_TRIPS)
                    .listener(createToolTraceListener(toolTrace))
                    .build();

            // 先落库用户消息（下次请求注入历史时包含本句）
            memoryService.saveMessage(request.getSessionId(), "user", userMessage, userId);
            userSaved = true;

            Object result = agent.invoke(Map.<String, Object>of("message", userMessage));
            String answer = result != null ? result.toString() : "";

            // 落库助手回复并带上本次请求的 token 合计
            memoryService.saveMessage(request.getSessionId(), "assistant", answer, userId, usage.totalTokens());

            long latency = System.currentTimeMillis() - startTime;

            return AIAgentResponse.builder()
                    .messageId(messageId)
                    .sessionId(request.getSessionId())
                    .content(answer)
                    .answer(answer)
                    .needHuman(false)
                    .suggestions(smartSuggestions(userMessage))
                    .toolCalls(toolTrace.isEmpty() ? null : toolTrace)
                    .tokensUsed(usage.totalTokens())
                    .latencyMs((int) latency)
                    .build();

        } catch (Exception e) {
            log.error("AI Agent处理失败: sessionId={}", request.getSessionId(), e);
            // AI 故障也要把"用户消息 + 故障回复"落库，否则刷新历史后像没发过一样
            String errorReply = "抱歉，我遇到了一些问题，请稍后再试或联系人工客服。";
            persistFallbackReply(request.getSessionId(), userMessage, userId, userSaved, errorReply, usage.totalTokens());
            return AIAgentResponse.builder()
                    .messageId(messageId)
                    .sessionId(request.getSessionId())
                    .content(errorReply)
                    .needHuman(true)
                    .tokensUsed(usage.totalTokens())
                    .build();
        } finally {
            // 请求结束清理 ThreadLocal，避免线程池复用导致跨请求串数
            tokenUsageTracker.end();
        }
    }

    /**
     * AI 故障/转人工等降级路径落库兜底：确保用户消息已存，再存一条助手回复，
     * 使用户刷新历史时能看到完整往返，而不是"像没发过"。
     */
    private void persistFallbackReply(String sessionId, String userMessage, Long userId,
                                      boolean userSaved, String assistantReply, Integer tokensUsed) {
        try {
            if (!userSaved) {
                memoryService.saveMessage(sessionId, "user", userMessage, userId);
            }
            memoryService.saveMessage(sessionId, "assistant", assistantReply, userId, tokensUsed);
        } catch (Exception ex) {
            log.warn("AI 降级回复落库失败: sessionId={}", sessionId, ex);
        }
    }

    /**
     * 从现有 ChatMemoryService（Redis + ai_chat_message 表）注入最近 5 轮对话到请求级内存窗口，
     * 保持多轮上下文连续，同时不改变 /ai/history 与 clearMemory 的既有语义。
     */
    private ChatMemory buildSeededMemory(String sessionId) {
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(12)
                .build();
        try {
            List<ChatMemoryService.MemoryMessage> history = memoryService.getRecentMessages(sessionId, 5);
            for (ChatMemoryService.MemoryMessage msg : history) {
                if ("user".equals(msg.getRole())) {
                    memory.add(UserMessage.from(msg.getContent()));
                } else if ("assistant".equals(msg.getRole())) {
                    memory.add(AiMessage.from(msg.getContent()));
                }
            }
        } catch (Exception e) {
            log.warn("注入历史记忆失败: sessionId={}", sessionId, e);
        }
        return memory;
    }

    /**
     * 观察 ReAct 循环每一步工具调用，收集 name/arguments/result 填充 toolCalls；
     * 并在 invoke 结束后从最终 ChatResponse 取真实 token 用量。
     */
    private AgentListener createToolTraceListener(List<ToolCall> trace) {
        return new AgentListener() {
            @Override
            public void afterAgentToolExecution(AfterAgentToolExecution observation) {
                ToolExecution exec = observation.toolExecution();
                ToolExecutionRequest req = exec.request();
                String result = exec.hasFailed() ? "工具执行失败: " + exec.result() : exec.result();
                trace.add(ToolCall.builder()
                        .id(req.id() != null ? req.id() : UUID.randomUUID().toString())
                        .name(req.name())
                        .arguments(req.arguments())
                        .result(result)
                        .build());
            }
        };
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
            5. 需要多个信息才能回答时，可以依次调用多个工具，直到获得完整答案
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

                // 仅当存在有效关键词时才拼接 AND(...) 条件，避免空条件生成 "WHERE (status=? AND )" 的 SQL 语法错误
                if (!keywords.isEmpty()) {
                    wrapper.and(w -> {
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
                }
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
