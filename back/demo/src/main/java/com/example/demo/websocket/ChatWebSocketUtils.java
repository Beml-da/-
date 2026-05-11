package com.example.demo.websocket;

import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatWebSocketUtils {
    public static final Map<Long, WebSocketSession> onlineUsers = new ConcurrentHashMap<>();

    public static void sendToUser(Long userId, String message) {
        WebSocketSession session = onlineUsers.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new org.springframework.web.socket.TextMessage(message));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
