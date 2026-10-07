package com.hongjie.pms.common.config;

import com.hongjie.pms.AI.websocket.AIAgentWebSocketHandler;
import com.hongjie.pms.modules.message.websocket.WebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final WebSocketHandler webSocketHandler;
    private final AIAgentWebSocketHandler aiAgentWebSocketHandler;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 消息通知 WebSocket
        // 用 allowedOriginPatterns 而非 allowedOrigins("*")：后者在 allowCredentials=true 时会触发
        // "allowedOrigins cannot contain '*'" 的校验异常（SockJS 场景下每个请求打一条 WARN）
        registry.addHandler(webSocketHandler, "/pet-system/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // AI 客服 WebSocket
        registry.addHandler(aiAgentWebSocketHandler, "/pet-system/ai/ws")
                .setAllowedOriginPatterns("*");
    }
}