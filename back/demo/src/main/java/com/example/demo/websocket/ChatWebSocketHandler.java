package com.example.demo.websocket;

import com.example.demo.ai.CustomerServiceAiService;
import com.example.demo.dto.ChatMessageVO;
import com.example.demo.mapper.ChatMessageMapper;
import com.example.demo.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final CustomerServiceAiService customerServiceAiService;

    @Autowired
    public ChatWebSocketHandler(ChatService chatService,
                               ChatMessageMapper messageMapper,
                               ChatWebSocketUtils chatWebSocketUtils,
                               CustomerServiceAiService customerServiceAiService) {
        this.chatService = chatService;
        this.messageMapper = messageMapper;
        this.chatWebSocketUtils = chatWebSocketUtils;
        this.customerServiceAiService = customerServiceAiService;
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
                case "ai-chat":
                    handleAiChatMessage(fromId, data, session);
                    break;
                case "ai-reset":
                    customerServiceAiService.clearContext(fromId);
                    try {
                        session.sendMessage(new TextMessage(
                                "{\"type\":\"ai-reset\",\"data\":{\"success\":true}}"));
                    } catch (IOException e) {
                        // ignore
                    }
                    break;
                case "ping":
                    chatWebSocketUtils.refreshOnline(fromId);
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

    /**
     * 处理 AI 客服消息：通过 WebSocket 实时流式响应。
     *
     * 流程：
     *  1. 把用户问题推回前端（type=ai-chat, role=user）
     *  2. 调 RAG 服务生成答案
     *  3. 流式把 AI 回复推回前端（type=ai-chat, role=assistant）
     */
    private void handleAiChatMessage(Long fromId, Map<String, Object> data, WebSocketSession session) {
        String content = (String) data.get("content");
        if (content == null || content.isBlank()) {
            return;
        }

        System.out.println("[WS] 收到 AI 客服消息 fromId=" + fromId + " content=" + content);

        try {
            // 1. 回显用户消息
            Map<String, Object> userEcho = new HashMap<>();
            userEcho.put("type", "ai-chat");
            userEcho.put("role", "user");
            userEcho.put("content", content);
            userEcho.put("fromId", fromId);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(userEcho)));

            // 2. 调 RAG 服务
            CustomerServiceAiService.CustomerAnswer answer =
                    customerServiceAiService.answer(fromId, content);

            // 3. 流式推送：把回复切成 5 字一包
            String reply = answer.answer();
            int chunkSize = 5;
            StringBuilder sent = new StringBuilder();
            for (int i = 0; i < reply.length(); i += chunkSize) {
                int end = Math.min(i + chunkSize, reply.length());
                String piece = reply.substring(i, end);
                sent.append(piece);

                Map<String, Object> aiChunk = new HashMap<>();
                aiChunk.put("type", "ai-chat");
                aiChunk.put("role", "assistant");
                aiChunk.put("content", piece);
                aiChunk.put("done", end >= reply.length());
                aiChunk.put("fromId", 0); // 0 表示 AI
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(aiChunk)));

                try {
                    Thread.sleep(30); // 模拟流式效果
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            System.out.println("[WS] AI 客服回复完成 fromId=" + fromId + " length=" + reply.length());

        } catch (Exception e) {
            System.out.println("[WS] AI 客服处理异常: " + e.getMessage());
            e.printStackTrace();
            try {
                Map<String, Object> err = new HashMap<>();
                err.put("type", "ai-chat");
                err.put("role", "error");
                err.put("content", "AI 客服暂时无法响应，请稍后再试");
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(err)));
            } catch (IOException ignored) {}
        }
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
