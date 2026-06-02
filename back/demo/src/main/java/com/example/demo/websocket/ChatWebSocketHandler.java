package com.example.demo.websocket;

import com.example.demo.dto.ChatMessageVO;
import com.example.demo.mapper.ChatMessageMapper;
import com.example.demo.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final ChatMessageMapper messageMapper;
    private final ChatWebSocketUtils chatWebSocketUtils;

    public ChatWebSocketHandler(ChatService chatService,
                               ChatMessageMapper messageMapper,
                               ChatWebSocketUtils chatWebSocketUtils) {
        this.chatService = chatService;
        this.messageMapper = messageMapper;
        this.chatWebSocketUtils = chatWebSocketUtils;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = getUserId(session);
        System.out.println("[WS] afterConnectionEstablished userId: " + userId + " sessionId: " + session.getId());
        if (userId != null) {
            chatWebSocketUtils.addOnlineUser(userId, session);
            System.out.println("[WS] 在线用户数: " + chatWebSocketUtils.onlineUserCount());
            deliverOfflineMessages(userId);
        }
    }

    private void deliverOfflineMessages(Long userId) {
        try {
            List<Object> offlineMsgIds = chatService.getOfflineMessageIds(userId);
            for (Object msgIdObj : offlineMsgIds) {
                Long msgId = Long.valueOf(msgIdObj.toString());
                ChatMessageVO msg = messageMapper.findById(msgId);
                if (msg != null) {
                    Map<String, Object> wsData = new HashMap<>();
                    wsData.put("type", "chat");
                    wsData.put("data", msg);
                    String json = objectMapper.writeValueAsString(wsData);
                    chatWebSocketUtils.sendMessageToOnlineUser(userId, json);
                    System.out.println("[WS] 离线消息已投递 msgId=" + msgId + " to userId=" + userId);
                }
            }
            if (!offlineMsgIds.isEmpty()) {
                chatService.clearOfflineMessages(userId);
            }
        } catch (Exception e) {
            System.out.println("[WS] 投递离线消息异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long fromId = getUserId(session);
        if (fromId == null) return;

        try {
            String payload = message.getPayload();
            @SuppressWarnings("unchecked")
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

        chatService.sendAndNotify(fromId, toId, content);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = getUserId(session);
        System.out.println("[WS] afterConnectionClosed userId: " + userId);
        if (userId != null) {
            chatWebSocketUtils.removeOnlineUser(userId);
            System.out.println("[WS] 在线用户数: " + chatWebSocketUtils.onlineUserCount());
        }
    }

    private Long getUserId(WebSocketSession session) {
        Map<String, Object> attrs = session.getAttributes();
        Object userId = attrs.get("userId");
        if (userId == null) return null;
        return Long.valueOf(userId.toString());
    }
}
