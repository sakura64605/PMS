package com.hongjie.pms.AI.modules.controller;

import com.hongjie.pms.AI.agent.AIAgentEngine;
import com.hongjie.pms.AI.modules.dto.request.AIAgentRequest;
import com.hongjie.pms.AI.modules.dto.response.AIAgentResponse;
import com.hongjie.pms.AI.modules.entity.AiChatMessage;
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
    
    @PostMapping("/session/create")
    public CommonResult<String> createSession() {
        String sessionId = chatSessionService.createSession(UserContext.getUserId());
        return CommonResult.success(sessionId);
    }
    
    @RedisRateLimit(key = "aiChat", capacity = 10, refillRate = 10, duration = 1, timeUnit = TimeUnit.MINUTES)
    @PostMapping("/chat")
    public CommonResult<AIAgentResponse> chat(@Valid @RequestBody AIAgentRequest request) {
        request.setUserId(UserContext.getUserId());
        AIAgentResponse response = aiAgentEngine.process(request);
        return CommonResult.success(response);
    }
    
    @GetMapping("/history/{sessionId}")
    public CommonResult<List<AiChatMessage>> getHistory(@PathVariable String sessionId) {
        List<AiChatMessage> history = chatSessionService.getSessionHistory(sessionId);
        return CommonResult.success(history);
    }
    
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