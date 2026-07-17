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
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    /**
     * 标记 session 已经因为编码/状态机错误而不可写，后续分片直接跳过
     */
    private final Set<String> sessionPoisoned = ConcurrentHashMap.newKeySet();

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
            List<ChatMessageVO> unreadMessages = messageMapper.findUnreadMessages(userId);
            for (ChatMessageVO msg : unreadMessages) {
                Map<String, Object> wsData = new HashMap<>();
                wsData.put("type", "chat");
                wsData.put("data", msg);
                String json = objectMapper.writeValueAsString(wsData);
                chatWebSocketUtils.sendMessageToOnlineUser(userId, json);
            }
            if (!unreadMessages.isEmpty()) {
                System.out.println("[WS] 离线消息已投递 " + unreadMessages.size() + " 条 to userId=" + userId);
            }
        } catch (Exception e) {
            System.out.println("[WS] 投递离线消息异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 串行化发送 WebSocket 文本消息，避免多线程/多次回调对同一 session 并发写
     * 而触发 Tomcat 的 TEXT_PARTIAL_WRITING 状态机非法错误。
     *
     * 同时在发送前用 UTF-8 严格编码校验过滤掉非法字符，避免
     * "Encoding error [MALFORMED[1]]" 把 partial 状态机打坏。
     * 一旦检测到不可恢复错误（编码异常或状态机非法），会把 session
     * 标记为 "poisoned"，后续同一 session 的所有 sendMessage 直接跳过。
     *
     * 锁表与 ChatWebSocketUtils 共享，保证全局同一 session 串行化。
     */
    /**
     * 过滤掉无法用 UTF-8 编码的字符（孤立的 surrogate 等），
     * 避免 Tomcat 在 sendPartialString 时抛 "Encoding error [MALFORMED[1]]"。
     *
     * 实现要点：每次调用都新建一个 CharsetEncoder，避免跨调用 / 跨线程的状态污染
     * （上一版用 ThreadLocal 时 encoder 在抛错后没及时 reset，导致后续 sanitize
     *  把正常字符串全部误判为非法，最终把所有切片过滤成空字符串）。
     */
    private String sanitizeUtf8(String input) {
        if (input == null || input.isEmpty()) return input;
        CharsetEncoder enc = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        StringBuilder sb = new StringBuilder(input.length());
        int i = 0;
        while (i < input.length()) {
            int cp = input.codePointAt(i);
            int charCount = Character.charCount(cp);
            // 编码单个码点前显式 reset，避免上一次失败残留 ERROR 状态
            enc.reset();
            try {
                enc.encode(java.nio.CharBuffer.wrap(input, i, i + charCount));
                sb.append(input, i, i + charCount);
            } catch (CharacterCodingException ex) {
                // 单字符失败：合法 surrogate pair 但配对错、或极罕见 unencodable
                sb.append('?');
            }
            i += charCount;
        }
        return sb.toString();
    }

    private void safeSend(WebSocketSession session, String json) {
        if (session == null || json == null || json.isEmpty()) return;
        String sid = session.getId();
        if (sessionPoisoned.contains(sid)) {
            return;
        }
        // 发送前严格 UTF-8 校验，过滤掉非法字符，避免 MALFORMED 把 partial 状态机打坏。
        // sanitize 在锁外完成（不持有 WebSocket 锁），且只用局部 encoder，
        // 与 WebSocket session 的部分写入状态机完全隔离。
        String safeJson = sanitizeUtf8(json);
        if (safeJson == null || safeJson.isEmpty()) {
            return;
        }
        Object lock = chatWebSocketUtils.lockFor(session);
        synchronized (lock) {
            if (!session.isOpen()) {
                sessionPoisoned.add(sid);
                return;
            }
            if (sessionPoisoned.contains(sid)) {
                return;
            }
            try {
                session.sendMessage(new TextMessage(safeJson));
            } catch (IOException e) {
                System.out.println("[WS] safeSend 失败 sessionId=" + sid + " err=" + e.getMessage());
                sessionPoisoned.add(sid);
            } catch (IllegalStateException e) {
                System.out.println("[WS] safeSend 状态非法 sessionId=" + sid + " err=" + e.getMessage());
                sessionPoisoned.add(sid);
            } catch (IllegalArgumentException e) {
                System.out.println("[WS] safeSend 编码非法 sessionId=" + sid + " err=" + e.getMessage());
                sessionPoisoned.add(sid);
            }
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
                    safeSend(session, "{\"type\":\"ai-reset\",\"data\":{\"success\":true}}");
                    break;
                case "ping":
                    chatWebSocketUtils.refreshOnline(fromId);
                    safeSend(session, "{\"type\":\"pong\"}");
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
            safeSend(session, objectMapper.writeValueAsString(userEcho));

            // 2. 调 RAG 服务
            CustomerServiceAiService.CustomerAnswer answer =
                    customerServiceAiService.answer(fromId, content);

            // 3. 流式推送：把回复切成 5 字一包
            String reply = answer.answer();
            int chunkSize = 5;
            StringBuilder sent = new StringBuilder();
            for (int i = 0; i < reply.length(); i += chunkSize) {
                // 一旦本 session 已经因为编码/状态机错误被标记为 poisoned，后续分片直接跳过
                if (sessionPoisoned.contains(session.getId())) {
                    System.out.println("[WS] session 已 poisoned，提前终止流式输出 fromId=" + fromId);
                    return;
                }
                if (!session.isOpen()) {
                    System.out.println("[WS] session 已关闭，提前终止流式输出 fromId=" + fromId);
                    return;
                }

                int end = Math.min(i + chunkSize, reply.length());
                String piece = reply.substring(i, end);
                sent.append(piece);

                Map<String, Object> aiChunk = new HashMap<>();
                aiChunk.put("type", "ai-chat");
                aiChunk.put("role", "assistant");
                aiChunk.put("content", piece);
                aiChunk.put("done", end >= reply.length());
                aiChunk.put("fromId", 0); // 0 表示 AI
                safeSend(session, objectMapper.writeValueAsString(aiChunk));

                try {
                    Thread.sleep(30); // 模拟流式效果
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            System.out.println("[WS] AI 客服回复完成 fromId=" + fromId + " length=" + reply.length());

        } catch (Exception e) {
            System.out.println("[WS] AI 客服处理异常: " + e.getMessage());
            e.printStackTrace();
            // 出错时不再强行往同一 session 写消息（上一个 send 可能已经把状态机打坏），
            // 仅尝试一次错误提示，交给 safeSend 兜底。
            try {
                Map<String, Object> err = new HashMap<>();
                err.put("type", "ai-chat");
                err.put("role", "error");
                err.put("content", "AI 客服暂时无法响应，请稍后再试");
                safeSend(session, objectMapper.writeValueAsString(err));
            } catch (Exception ignored) {
            }
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
        String sid = session.getId();
        // 清理会话级锁和中毒标记，防止长期运行无界增长
        chatWebSocketUtils.clearLockFor(session);
        sessionPoisoned.remove(sid);
    }

    private Long getUserId(WebSocketSession session) {
        Map<String, Object> attrs = session.getAttributes();
        Object userId = attrs.get("userId");
        if (userId == null) return null;
        return Long.valueOf(userId.toString());
    }
}
