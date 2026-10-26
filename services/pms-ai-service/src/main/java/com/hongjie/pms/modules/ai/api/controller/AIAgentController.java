package com.hongjie.pms.modules.ai.api.controller;

import com.hongjie.pms.modules.ai.application.AiConversationGateway;
import com.hongjie.pms.modules.ai.api.dto.request.AIAgentRequest;
import com.hongjie.pms.modules.ai.api.dto.response.AIAgentResponse;
import com.hongjie.pms.modules.ai.infrastructure.persistence.entity.AiChatMessage;
import com.hongjie.pms.modules.ai.infrastructure.persistence.entity.AiChatSession;
import com.hongjie.pms.modules.ai.api.service.ChatSessionService;
import com.hongjie.pms.modules.ai.api.service.HumanTransferService;
import com.hongjie.pms.platform.annotation.RedisRateLimit;
import com.hongjie.pms.platform.base.core.UserContext;
import com.hongjie.pms.platform.pojo.CommonResult;
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

    private final AiConversationGateway conversations;
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
        Long userId = UserContext.getUserId();
        if (!chatSessionService.checkSessionOwner(sessionId, userId)) {
            return CommonResult.success(List.of());
        }
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
        return CommonResult.success(conversations.process(UserContext.getUserId(), request));
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
    @DeleteMapping("/memory/{sessionId:[a-zA-Z0-9_-]{20,64}}")
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
