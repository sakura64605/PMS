package com.hongjie.pms.AI.websocket;

import com.alibaba.fastjson2.JSON;
import com.hongjie.pms.AI.agent.AIAgentEngine;
import com.hongjie.pms.AI.modules.dto.request.AIAgentRequest;
import com.hongjie.pms.AI.modules.dto.response.AIAgentResponse;
import com.hongjie.pms.common.utils.JWTUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AIAgentWebSocketHandler extends TextWebSocketHandler {
    
    private final AIAgentEngine aiAgentEngine;
    private final JWTUtils jwtUtils;
    
    private static final Map<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 从 URL 参数中获取 token 并验证
        String token = getTokenFromSession(session);
        if (token == null || !jwtUtils.validateToken(token)) {
            log.warn("AI WebSocket 连接未认证或 token 无效");
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE);
            } catch (Exception e) {
                log.error("关闭非法连接失败", e);
            }
            return;
        }
        
        Long userId = jwtUtils.getUserId(token);
        String sessionId = session.getId();
        SESSIONS.put(sessionId, session);
        log.info("AI客服WebSocket连接建立: sessionId={}, userId={}", sessionId, userId);
        
        sendMessage(sessionId, AIAgentResponse.builder()
                .messageId(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .content("您好！我是AI客服助手😊 有什么可以帮您的吗？")
                .build());
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = session.getId();
        
        try {
            Map<String, Object> data = JSON.parseObject(message.getPayload());
            String userMessage = (String) data.get("message");
            
            AIAgentRequest request = new AIAgentRequest();
            request.setSessionId(sessionId);
            request.setMessage(userMessage);
            request.setStream(false);
            
            AIAgentResponse response = aiAgentEngine.process(request);
            sendMessage(sessionId, response);
            
        } catch (Exception e) {
            log.error("处理消息失败", e);
            sendMessage(sessionId, AIAgentResponse.builder()
                    .messageId(UUID.randomUUID().toString())
                    .sessionId(sessionId)
                    .content("抱歉，处理出错了，请稍后再试。")
                    .needHuman(true)
                    .build());
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SESSIONS.remove(session.getId());
        log.info("AI客服WebSocket连接关闭: sessionId={}", session.getId());
    }
    
    private void sendMessage(String sessionId, AIAgentResponse response) {
        WebSocketSession session = SESSIONS.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(JSON.toJSONString(response)));
            } catch (Exception e) {
                log.error("发送消息失败", e);
            }
        }
    }
    
    /**
     * 从 WebSocket 会话中获取 token
     */
    private String getTokenFromSession(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query != null && !query.isEmpty()) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("token=")) {
                    return param.substring(6);
                }
            }
        }
        return null;
    }
}