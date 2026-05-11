package com.example.demo.websocket;

import com.example.demo.common.JwtUtil;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        System.out.println("[WS] 收到握手请求: " + request.getURI());
        // 从 URL 参数中取 token（WebSocket 连接格式: /ws/chat?token=xxx）
        String uri = request.getURI().toString();
        String token = extractToken(uri);
        System.out.println("[WS] token: " + (token != null ? token.substring(0, Math.min(20, token.length())) + "..." : "null"));
        if (token != null && JwtUtil.validateToken(token)) {
            Long userId = JwtUtil.getUserIdFromToken(token);
            System.out.println("[WS] 握手成功 userId: " + userId);
            attributes.put("userId", userId);
            return true;
        }
        System.out.println("[WS] 握手失败，token无效");
        return false;
    }

    private String extractToken(String uri) {
        int idx = uri.indexOf("token=");
        if (idx < 0) return null;
        String rest = uri.substring(idx + 6);
        int end = rest.indexOf("&");
        return end >= 0 ? rest.substring(0, end) : rest;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
