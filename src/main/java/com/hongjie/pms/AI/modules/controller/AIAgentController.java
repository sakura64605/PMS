package com.hongjie.pms.AI.modules.controller;

import com.hongjie.pms.AI.agent.AIAgentEngine;
import com.hongjie.pms.AI.modules.dto.request.AIAgentRequest;
import com.hongjie.pms.AI.modules.dto.response.AIAgentResponse;
import com.hongjie.pms.AI.modules.entity.AiChatMessage;
import com.hongjie.pms.AI.modules.entity.AiChatSession;
import com.hongjie.pms.AI.modules.service.ChatSessionService;
import com.hongjie.pms.AI.modules.service.HumanTransferService;
import com.hongjie.pms.common.annotation.RedisRateLimit;
import com.hongjie.pms.common.base.core.UserContext;
import com.hongjie.pms.common.pojo.CommonResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/pet-system/ai")
@RequiredArgsConstructor
public class AIAgentController {

    private final AIAgentEngine aiAgentEngine;
    private final ChatSessionService chatSessionService;
    private final HumanTransferService humanTransferService;

    /**
     * 获取用户的所有会话列表（用于左侧历史记录）
     */
    @GetMapping("/sessions")
    public CommonResult<List<AiChatSession>> getUserSessions() {
        List<AiChatSession> sessions = chatSessionService.getUserSessions(UserContext.getUserId());
        return CommonResult.success(sessions);
    }

    /**
     * 创建新会话（用户点击"新对话"时调用）
     */
    @PostMapping("/session/create")
    public CommonResult<String> createSession() {
        String sessionId = chatSessionService.createSession(UserContext.getUserId());
        return CommonResult.success(sessionId);
    }

    /**
     * 获取会话历史消息
     */
    @GetMapping("/history/{sessionId}")
    public CommonResult<List<AiChatMessage>> getHistory(@PathVariable String sessionId) {
        List<AiChatMessage> history = chatSessionService.getSessionHistory(sessionId);
        return CommonResult.success(history);
    }

    /**
     * 发送消息（核心接口）
     * 如果 sessionId 为空，后端自动创建新会话
     */
    @RedisRateLimit(key = "aiChat", capacity = 10, refillRate = 10, duration = 1, timeUnit = TimeUnit.MINUTES)
    @PostMapping("/chat")
    public CommonResult<AIAgentResponse> chat(@Valid @RequestBody AIAgentRequest request) {
        Long userId = UserContext.getUserId();
        request.setUserId(userId);

        // 关键逻辑：如果没有 sessionId，自动创建
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = chatSessionService.createSession(userId);
            request.setSessionId(sessionId);
            log.info("用户 {} 首次发送消息，自动创建会话: {}", userId, sessionId);
        }

        AIAgentResponse response = aiAgentEngine.process(request);

        // 将 sessionId 返回给前端，前端需要保存用于后续对话
        response.setSessionId(sessionId);

        return CommonResult.success(response);
    }

    /**
     * 删除会话（用户删除对话记录）
     */
    @DeleteMapping("/session/{sessionId}")
    public CommonResult<String> deleteSession(@PathVariable String sessionId) {
        chatSessionService.deleteSession(sessionId, UserContext.getUserId());
        return CommonResult.success("会话已删除");
    }

    /**
     * 清空会话记忆（清除 Redis 缓存，保留数据库记录）
     */
    @DeleteMapping("/memory/{sessionId}")
    public CommonResult<String> clearMemory(@PathVariable String sessionId) {
        chatSessionService.clearMemory(sessionId);
        return CommonResult.success("会话记忆已清除");
    }

    @PostMapping("/transfer")
    public CommonResult<String> transferToHuman(@RequestParam String sessionId, @RequestParam(required = false) String reason) {
        humanTransferService.requestTransfer(sessionId, UserContext.getUserId(), reason);
        return CommonResult.success("已为您转接人工客服，请稍候...");
    }

    @PostMapping("/feedback")
    public CommonResult<String> submitFeedback(@RequestParam String messageId, @RequestParam Integer score) {
        chatSessionService.submitFeedback(messageId, score);
        return CommonResult.success("感谢您的反馈！");
    }

    @GetMapping("/suggestions")
    public CommonResult<List<String>> getSuggestions() {
        return CommonResult.success(List.of(
                "如何发布领养信息？",
                "平台有哪些活动？",
                "怎么报名参加活动？",
                "如何联系人工客服？"
        ));
    }
}