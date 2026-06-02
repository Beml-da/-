package com.example.demo.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.socket.TextMessage;

@Component
public class ChatWebSocketUtils {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public boolean isUserOnline(Long userId) {
        if (redisTemplate == null) return false;
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember("online:users", userId.toString()));
    }

    public void addOnlineUser(Long userId, WebSocketSession session) {
        if (redisTemplate == null) return;
        sessions.put(userId, session);
        redisTemplate.opsForSet().add("online:users", userId.toString());
    }

    public void removeOnlineUser(Long userId) {
        if (redisTemplate == null) return;
        sessions.remove(userId);
        redisTemplate.opsForSet().remove("online:users", userId.toString());
    }

    public int onlineUserCount() {
        return sessions.size();
    }

    public void sendMessageToOnlineUser(Long userId, String json) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
