package com.example.demo.service.impl;

import com.example.demo.dto.ChatMessageVO;
import com.example.demo.dto.ChatSessionVO;
import com.example.demo.entity.ChatMessage;
import com.example.demo.entity.ChatSession;
import com.example.demo.mapper.ChatMessageMapper;
import com.example.demo.mapper.ChatSessionMapper;
import com.example.demo.service.ChatService;
import com.example.demo.websocket.ChatWebSocketUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    private static final int SESSION_TTL = 5;
    private static final int HISTORY_TTL = 10;

    private final ChatMessageMapper messageMapper;
    private final ChatSessionMapper sessionMapper;
    private final ChatWebSocketUtils chatWebSocketUtils;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final com.example.demo.mapper.FriendRelationMapper friendRelationMapper;

    public ChatServiceImpl(ChatMessageMapper messageMapper,
                           ChatSessionMapper sessionMapper,
                           ChatWebSocketUtils chatWebSocketUtils,
                           StringRedisTemplate redisTemplate,
                           com.example.demo.mapper.FriendRelationMapper friendRelationMapper) {
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
        this.chatWebSocketUtils = chatWebSocketUtils;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.friendRelationMapper = friendRelationMapper;
    }

    @Override
    @Transactional
    public Map<String, Object> saveMessage(Long fromId, Long toId, String content, Long sessionId) {
        System.out.println("[ChatService] saveMessage fromId=" + fromId + " toId=" + toId + " content=" + content);

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

        ChatMessage msg = new ChatMessage(fromId, toId, content);
        msg.setSessionId(session1.getId());
        messageMapper.insert(msg);

        sessionMapper.updateLastMessage(session1.getId(), msg.getId());

        Long chatSessionId = session2 != null ? session2.getId() : session1.getId();
        sessionMapper.incrementUnread(toId, fromId);

        String unreadKey = "unread:" + chatSessionId + ":" + toId;
        redisTemplate.opsForValue().increment(unreadKey);
        redisTemplate.expire(unreadKey, 24, TimeUnit.HOURS);

        evictSessionCache(fromId);
        evictSessionCache(toId);
        evictHistoryCache(fromId, toId);
        evictHistoryCache(toId, fromId);

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
        if (friendRelationMapper.findRelationEither(fromId, toId) == null) {
            System.out.println("[ChatService] 双方已不是好友，拒绝发送 fromId=" + fromId + " toId=" + toId);
            pushSendFailure(fromId, toId, content);
            return;
        }

        Map<String, Object> saved = saveMessage(fromId, toId, content, null);

        ChatMessageVO msgVO = messageMapper.findById((Long) saved.get("id"));
        if (msgVO == null) {
            System.out.println("[ChatService] 消息保存失败，msgVO为null");
            return;
        }
        msgVO.setStatus("sent");

        try {
            Map<String, Object> wsData = new HashMap<>();
            wsData.put("type", "chat");
            wsData.put("data", msgVO);
            String json = objectMapper.writeValueAsString(wsData);

            Boolean toOnline = chatWebSocketUtils.isUserOnline(toId);
            Boolean fromOnline = chatWebSocketUtils.isUserOnline(fromId);
            System.out.println("[ChatService] 推送消息给 toId=" + toId + " online=" + toOnline);
            System.out.println("[ChatService] 推送确认给 fromId=" + fromId + " online=" + fromOnline);

            if (Boolean.TRUE.equals(toOnline)) {
                chatWebSocketUtils.sendMessageToOnlineUser(toId, json);
            }

            if (Boolean.TRUE.equals(fromOnline)) {
                chatWebSocketUtils.sendMessageToOnlineUser(fromId, json);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void pushSendFailure(Long fromId, Long toId, String content) {
        try {
            ChatMessageVO failVO = new ChatMessageVO();
            failVO.setId(-System.currentTimeMillis());
            failVO.setFromId(fromId);
            failVO.setToId(toId);
            failVO.setContent(content);
            failVO.setStatus("failed");
            failVO.setCreateTime(java.time.LocalDateTime.now());

            Map<String, Object> wsData = new HashMap<>();
            wsData.put("type", "chat");
            wsData.put("data", failVO);
            String json = objectMapper.writeValueAsString(wsData);

            if (Boolean.TRUE.equals(chatWebSocketUtils.isUserOnline(fromId))) {
                chatWebSocketUtils.sendMessageToOnlineUser(fromId, json);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<ChatMessageVO> getHistory(Long userId, Long targetUserId) {
        String key = buildHistoryCacheKey(userId, targetUserId);
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<ChatMessageVO>>() {});
            }
        } catch (Exception e) {
            log.error("Redis GET 失败, key={}", key, e);
        }
        List<ChatMessageVO> messages = messageMapper.findHistory(userId, targetUserId);
        messageMapper.markRead(userId, targetUserId);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(messages), HISTORY_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Redis SET 失败, key={}", key, e);
        }
        return messages;
    }

    @Override
    public List<ChatSessionVO> getSessions(Long userId) {
        String key = "chat:session:" + userId;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                List<ChatSessionVO> sessions = objectMapper.readValue(cached, new TypeReference<List<ChatSessionVO>>() {});
                for (ChatSessionVO s : sessions) {
                    s.setTargetOnline(
                        chatWebSocketUtils.isUserOnline(s.getTargetUserId()) ? 1 : 0
                    );
                }
                return sessions;
            }
        } catch (Exception e) {
            log.error("Redis GET 失败, key={}", key, e);
        }
        List<ChatSessionVO> sessions = sessionMapper.findSessionsWithDetail(userId);
        for (ChatSessionVO s : sessions) {
            s.setTargetOnline(
                chatWebSocketUtils.isUserOnline(s.getTargetUserId()) ? 1 : 0
            );
        }
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(sessions), SESSION_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Redis SET 失败, key={}", key, e);
        }
        return sessions;
    }

    @Override
    public void markRead(Long userId, Long fromId) {
        messageMapper.markRead(userId, fromId);
        ChatSession session = sessionMapper.findByUsers(userId, fromId);
        if (session != null) {
            redisTemplate.delete("unread:" + session.getId() + ":" + userId);
        }
        evictSessionCache(userId);
        evictHistoryCache(userId, fromId);
    }

    @Override
    public int getTotalUnread(Long userId) {
        List<ChatSessionVO> sessions = sessionMapper.findSessionsWithDetail(userId);
        int total = 0;
        for (ChatSessionVO s : sessions) {
            String unreadKey = "unread:" + s.getId() + ":" + userId;
            String cached = redisTemplate.opsForValue().get(unreadKey);
            if (cached != null) {
                total += Integer.parseInt(cached);
            } else {
                total += s.getUnreadCount() != null ? s.getUnreadCount() : 0;
            }
        }
        return total;
    }

    @Override
    public void clearUnread(Long userId, Long targetUserId) {
        sessionMapper.clearUnread(userId, targetUserId);
        messageMapper.markRead(userId, targetUserId);
        ChatSession session = sessionMapper.findByUsers(userId, targetUserId);
        if (session != null) {
            redisTemplate.delete("unread:" + session.getId() + ":" + userId);
        }
        evictSessionCache(userId);
        evictHistoryCache(userId, targetUserId);
    }

    private void evictSessionCache(Long userId) {
        try {
            redisTemplate.delete("chat:session:" + userId);
        } catch (Exception e) {
            log.error("删除会话列表缓存失败, userId={}", userId, e);
        }
    }

    private void evictHistoryCache(Long userId, Long targetUserId) {
        try {
            redisTemplate.delete(buildHistoryCacheKey(userId, targetUserId));
        } catch (Exception e) {
            log.error("删除聊天记录缓存失败, userId={} targetUserId={}", userId, targetUserId, e);
        }
    }

    private String buildHistoryCacheKey(Long userId, Long targetUserId) {
        return String.format("chat:history:%d:%d", Math.min(userId, targetUserId), Math.max(userId, targetUserId));
    }
}
