package com.example.demo.entity;

import java.time.LocalDateTime;

public class FriendRelation {
    private Long id;
    private Long userId;
    private Long friendId;
    private LocalDateTime createTime;
    private Integer deleted;

    public FriendRelation() {}

    public FriendRelation(Long userId, Long friendId) {
        this.userId = userId;
        this.friendId = friendId;
        this.deleted = 0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getFriendId() { return friendId; }
    public void setFriendId(Long friendId) { this.friendId = friendId; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
