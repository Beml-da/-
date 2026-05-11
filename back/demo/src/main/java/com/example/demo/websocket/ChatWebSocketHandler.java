package com.example.demo.websocket;

import com.example.demo.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatService chatService;

    public ChatWebSocketHandler(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = getUserId(session);
        System.out.println("[WS] afterConnectionEstablished userId: " + userId + " sessionId: " + session.getId());
        if (userId != null) {
            ChatWebSocketUtils.onlineUsers.put(userId, session);
            System.out.println("[WS] 在线用户数: " + ChatWebSocketUtils.onlineUsers.size());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long fromId = getUserId(session);
        if (fromId == null) return;

        try {
            String payload = message.getPayload();
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String type = (String) data.get("type");

            switch (type) {
                case "chat":
                    handleChatMessage(fromId, data);
                    break;
                case "ping":
                    try {
                        session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
                    } catch (IOException e) {
                        System.out.println("[WS] ping回复失败: " + e.getMessage());
                    }
                    break;
            }
        } catch (Exception e) {
            System.out.println("[WS] 处理消息异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleChatMessage(Long fromId, Map<String, Object> data) throws IOException {
        System.out.println("[WS] 收到聊天消息 fromId: " + fromId + " data: " + data);
        Long toId = ((Number) data.get("toId")).longValue();
        String content = (String) data.get("content");

        // 保存消息到数据库，并发 WS 通知
        chatService.sendAndNotify(fromId, toId, content);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = getUserId(session);
        System.out.println("[WS] afterConnectionClosed userId: " + userId);
        if (userId != null) {
            ChatWebSocketUtils.onlineUsers.remove(userId);
            System.out.println("[WS] 在线用户数: " + ChatWebSocketUtils.onlineUsers.size());
        }
    }

    private Long getUserId(WebSocketSession session) {
        Map<String, Object> attrs = session.getAttributes();
        Object userId = attrs.get("userId");
        if (userId == null) return null;
        return Long.valueOf(userId.toString());
    }
}
