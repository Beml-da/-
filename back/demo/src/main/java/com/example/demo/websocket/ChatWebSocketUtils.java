package com.example.demo.websocket;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketUtils {

    private static final String ONLINE_KEY = "online:users";

    /** 没有心跳的 member 视为离线（秒） */
    private static final long ONLINE_TTL_SECONDS = 60;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** 按 session 串行化发送，避免 Tomcat WebSocket 状态机进入非法状态 */
    private static final Map<String, Object> sessionLocks = new ConcurrentHashMap<>();

    /**
     * 服务启动时清空 online 集合：
     * 1) 清理上一次进程残留的 userId（断网/kill 进程时 Redis 没收到 remove）
     * 2) 避免开发期反复重启后历史脏数据导致"离线也显示在线"
     */
    @PostConstruct
    public void clearOnStartup() {
        try {
            Boolean deleted = redisTemplate.delete(ONLINE_KEY);
            System.out.println("[WS] 启动清理 online:users, deleted=" + deleted);
        } catch (Exception e) {
            System.out.println("[WS] 启动清理 online:users 失败: " + e.getMessage());
        }
    }

    public boolean isUserOnline(Long userId) {
        if (redisTemplate == null) return false;
        if (userId == null) return false;
        // 先用进程内 sessions 兜底（解决单机环境历史脏数据）
        if (sessions.containsKey(userId)) {
            return true;
        }
        Double score = redisTemplate.opsForZSet().score(ONLINE_KEY, userId.toString());
        if (score == null) return false;
        long ageSec = (System.currentTimeMillis() - score.longValue()) / 1000;
        return ageSec <= ONLINE_TTL_SECONDS;
    }

    public void addOnlineUser(Long userId, WebSocketSession session) {
        if (redisTemplate == null || userId == null) return;
        sessions.put(userId, session);
        refreshOnline(userId);
    }

    public void removeOnlineUser(Long userId) {
        if (redisTemplate == null || userId == null) return;
        sessions.remove(userId);
        try {
            redisTemplate.opsForZSet().remove(ONLINE_KEY, userId.toString());
        } catch (Exception e) {
            System.out.println("[WS] 移除在线状态失败: " + e.getMessage());
        }
    }

    /** 心跳续期 */
    public void refreshOnline(Long userId) {
        if (redisTemplate == null || userId == null) return;
        try {
            redisTemplate.opsForZSet().add(ONLINE_KEY, userId.toString(), System.currentTimeMillis());
        } catch (Exception e) {
            System.out.println("[WS] 刷新在线状态失败: " + e.getMessage());
        }
    }

    public int onlineUserCount() {
        if (redisTemplate == null) return sessions.size();
        try {
            long now = System.currentTimeMillis();
            double cutoff = now - ONLINE_TTL_SECONDS * 1000;
            // 清掉已过期的 member，避免长期累积
            redisTemplate.opsForZSet().removeRangeByScore(ONLINE_KEY, 0, cutoff);
            Long size = redisTemplate.opsForZSet().zCard(ONLINE_KEY);
            return size == null ? 0 : size.intValue();
        } catch (Exception e) {
            return sessions.size();
        }
    }

    public Set<String> getOnlineUserIds() {
        if (redisTemplate == null) return Set.of();
        long now = System.currentTimeMillis();
        double cutoff = now - ONLINE_TTL_SECONDS * 1000;
        try {
            redisTemplate.opsForZSet().removeRangeByScore(ONLINE_KEY, 0, cutoff);
            return redisTemplate.opsForZSet().range(ONLINE_KEY, 0, -1);
        } catch (Exception e) {
            return Set.of();
        }
    }

    public void sendMessageToOnlineUser(Long userId, String json) {
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) {
            return;
        }
        if (json == null || json.isEmpty()) {
            return;
        }
        Object lock = lockFor(session);
        synchronized (lock) {
            if (!session.isOpen()) {
                return;
            }
            safeSendOnSession(session, json);
        }
    }

    /**
     * 获取 session 级别的串行化锁。外部需要保证同一 session 上所有发送都共用此锁，
     * 避免 Tomcat WebSocket 状态机进入非法 TEXT_PARTIAL_WRITING。
     */
    public Object lockFor(WebSocketSession session) {
        return sessionLocks.computeIfAbsent(session.getId(), k -> new Object());
    }

    /** WebSocket 断开后清理该 session 的串行化锁，防止长期运行后 sessionLocks 无界增长 */
    public void clearLockFor(WebSocketSession session) {
        if (session != null) {
            sessionLocks.remove(session.getId());
        }
    }

    /**
     * 在已持有的 session 上串行化发送：空字符串过滤 + 状态机异常兜底。
     * 调用方需自行在外层 synchronized(lockFor(session))。
     */
    public void safeSendOnSession(WebSocketSession session, String json) {
        if (session == null || json == null || json.isEmpty()) {
            return;
        }
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(json));
        } catch (IOException | IllegalStateException | IllegalArgumentException e) {
            System.out.println("[WS] sendMessageToOnlineUser 失败 sessionId="
                    + session.getId() + " err=" + e.getMessage());
        }
    }
}
