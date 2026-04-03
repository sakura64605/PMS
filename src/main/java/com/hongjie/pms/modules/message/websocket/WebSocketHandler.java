package com.hongjie.pms.modules.message.websocket;

import com.alibaba.fastjson2.JSON;
import com.hongjie.pms.common.utils.JWTUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketHandler extends TextWebSocketHandler {

    private final JWTUtils jwtUtils;

    // 在线用户: userId -> session
    private static final Map<Long, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = getTokenFromSession(session);
        if (token == null || !jwtUtils.validateToken(token)) {
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE);
            } catch (Exception e) {
                log.error("关闭非法连接失败", e);
            }
            return;
        }

        Long userId = jwtUtils.getUserId(token);
        SESSIONS.put(userId, session);
        log.info("用户 {} 已连接，在线人数: {}", userId, SESSIONS.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SESSIONS.entrySet().removeIf(entry -> entry.getValue().equals(session));
        log.info("用户断开连接，在线人数: {}", SESSIONS.size());
    }

    /**
     * 推送消息给指定用户
     */
    public static boolean pushToUser(Long userId, Object message) {
        WebSocketSession session = SESSIONS.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String json = JSON.toJSONString(message);
                session.sendMessage(new TextMessage(json));
                log.debug("推送成功: userId={}", userId);
                return true;
            } catch (Exception e) {
                log.error("推送失败: userId={}", userId, e);
                SESSIONS.remove(userId);
            }
        }
        return false;
    }

    /**
     * 获取在线用户数量
     */
    public static int getOnlineCount() {
        return SESSIONS.size();
    }

    private String getTokenFromSession(WebSocketSession session) {
        // 从 URL 参数获取: ws://localhost/ws?token=xxx
        String query = session.getUri().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    return param.substring(6);
                }
            }
        }
        // 从 Header 获取
        return session.getHandshakeHeaders().getFirst("Authorization");
    }
}