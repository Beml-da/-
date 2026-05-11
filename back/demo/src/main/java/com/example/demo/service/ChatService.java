package com.example.demo.service;

import com.example.demo.dto.ChatMessageVO;
import com.example.demo.dto.ChatSessionVO;
import java.util.List;
import java.util.Map;

public interface ChatService {
    Map<String, Object> saveMessage(Long fromId, Long toId, String content, Long sessionId);
    void sendAndNotify(Long fromId, Long toId, String content);
    List<ChatMessageVO> getHistory(Long userId, Long targetUserId);
    List<ChatSessionVO> getSessions(Long userId);
    void markRead(Long userId, Long fromId);
    int getTotalUnread(Long userId);
    void clearUnread(Long userId, Long targetUserId);
}
