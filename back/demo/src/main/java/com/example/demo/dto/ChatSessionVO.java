package com.example.demo.dto;

import java.time.LocalDateTime;

public class ChatSessionVO {
    private Long id;
    private Long userId;
    private Long targetUserId;
    private String targetNickname;
    private String targetAvatar;
    private Integer targetOnline;
    private Integer unreadCount;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    public String getTargetNickname() { return targetNickname; }
    public void setTargetNickname(String targetNickname) { this.targetNickname = targetNickname; }
    public String getTargetAvatar() { return targetAvatar; }
    public void setTargetAvatar(String targetAvatar) { this.targetAvatar = targetAvatar; }
    public Integer getTargetOnline() { return targetOnline; }
    public void setTargetOnline(Integer targetOnline) { this.targetOnline = targetOnline; }
    public Integer getUnreadCount() { return unreadCount; }
    public void setUnreadCount(Integer unreadCount) { this.unreadCount = unreadCount; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public LocalDateTime getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(LocalDateTime lastMessageTime) { this.lastMessageTime = lastMessageTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
