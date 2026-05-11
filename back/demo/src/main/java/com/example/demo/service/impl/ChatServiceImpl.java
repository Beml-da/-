package com.example.demo.service.impl;

import com.example.demo.dto.ChatMessageVO;
import com.example.demo.dto.ChatSessionVO;
import com.example.demo.entity.ChatMessage;
import com.example.demo.entity.ChatSession;
import com.example.demo.mapper.ChatMessageMapper;
import com.example.demo.mapper.ChatSessionMapper;
import com.example.demo.service.ChatService;
import com.example.demo.websocket.ChatWebSocketUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatServiceImpl implements ChatService {

    private final ChatMessageMapper messageMapper;
    private final ChatSessionMapper sessionMapper;
    private final ObjectMapper objectMapper;

    public ChatServiceImpl(ChatMessageMapper messageMapper, ChatSessionMapper sessionMapper) {
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    @Transactional
    public Map<String, Object> saveMessage(Long fromId, Long toId, String content, Long sessionId) {
        System.out.println("[ChatService] saveMessage fromId=" + fromId + " toId=" + toId + " content=" + content);

        // 获取或创建会话（双向）
        ChatSession session1 = sessionMapper.findByUsers(fromId, toId);
        if (session1 == null) {
            ChatSession s = new ChatSession(fromId, toId);
            sessionMapper.insert(s);
            session1 = s;
        }
        ChatSession session2 = sessionMapper.findByUsers(toId, fromId);
        if (session2 == null) {
            ChatSession s = new ChatSession(toId, fromId);
            sessionMapper.insert(s);
        }

        // 保存消息
        ChatMessage msg = new ChatMessage(fromId, toId, content);
        msg.setSessionId(session1.getId());
        messageMapper.insert(msg);

        // 更新会话的最后消息ID
        sessionMapper.updateLastMessage(session1.getId(), msg.getId());

        // 增加未读数（接收者）
        sessionMapper.incrementUnread(toId, fromId);

        Map<String, Object> result = new HashMap<>();
        result.put("id", msg.getId());
        result.put("sessionId", session1.getId());
        result.put("fromId", fromId);
        result.put("toId", toId);
        result.put("content", content);
        result.put("createTime", msg.getCreateTime());
        return result;
    }

    @Override
    public void sendAndNotify(Long fromId, Long toId, String content) {
        Map<String, Object> saved = saveMessage(fromId, toId, content, null);

        // 构造消息 VO（带发送者信息）
        ChatMessageVO msgVO = messageMapper.findById((Long) saved.get("id"));
        if (msgVO == null) {
            System.out.println("[ChatService] 消息保存失败，msgVO为null");
            return;
        }

        try {
            Map<String, Object> wsData = new HashMap<>();
            wsData.put("type", "chat");
            wsData.put("data", msgVO);
            String json = objectMapper.writeValueAsString(wsData);

            System.out.println("[ChatService] 推送消息给 toId=" + toId + " online=" + ChatWebSocketUtils.onlineUsers.containsKey(toId));
            System.out.println("[ChatService] 推送确认给 fromId=" + fromId + " online=" + ChatWebSocketUtils.onlineUsers.containsKey(fromId));

            // 发给接收者（如果在线）
            ChatWebSocketUtils.sendToUser(toId, json);
            // 发回给发送者（确认）
            ChatWebSocketUtils.sendToUser(fromId, json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<ChatMessageVO> getHistory(Long userId, Long targetUserId) {
        List<ChatMessageVO> messages = messageMapper.findHistory(userId, targetUserId);
        messageMapper.markRead(userId, targetUserId);
        return messages;
    }

    @Override
    public List<ChatSessionVO> getSessions(Long userId) {
        List<ChatSessionVO> sessions = sessionMapper.findSessionsWithDetail(userId);
        for (ChatSessionVO s : sessions) {
            s.setTargetOnline(
                ChatWebSocketUtils.onlineUsers.containsKey(s.getTargetUserId()) ? 1 : 0
            );
        }
        return sessions;
    }

    @Override
    public void markRead(Long userId, Long fromId) {
        messageMapper.markRead(userId, fromId);
    }

    @Override
    public int getTotalUnread(Long userId) {
        return messageMapper.countUnread(userId);
    }

    @Override
    public void clearUnread(Long userId, Long targetUserId) {
        sessionMapper.clearUnread(userId, targetUserId);
        messageMapper.markRead(userId, targetUserId);
    }
}
