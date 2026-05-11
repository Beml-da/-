package com.example.demo.entity;

import java.time.LocalDateTime;

public class ChatSession {
    private Long id;
    private Long userId;
    private Long targetUserId;
    private Long lastMessageId;
    private Integer unreadCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public ChatSession() {}

    public ChatSession(Long userId, Long targetUserId) {
        this.userId = userId;
        this.targetUserId = targetUserId;
        this.unreadCount = 0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    public Long getLastMessageId() { return lastMessageId; }
    public void setLastMessageId(Long lastMessageId) { this.lastMessageId = lastMessageId; }
    public Integer getUnreadCount() { return unreadCount; }
    public void setUnreadCount(Integer unreadCount) { this.unreadCount = unreadCount; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
