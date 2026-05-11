package com.example.demo.dto;

import com.example.demo.entity.FriendRequest;
import com.example.demo.entity.User;

public class FriendRequestVO {
    private Long id;
    private Long fromUserId;
    private String fromNickname;
    private String fromAvatar;
    private String fromSchool;
    private Integer fromCreditScore;
    private String message;
    private String createTime;

    public FriendRequestVO() {}

    public FriendRequestVO(FriendRequest req, User fromUser) {
        this.id = req.getId();
        this.fromUserId = req.getFromUserId();
        this.fromNickname = fromUser.getNickname();
        this.fromAvatar = fromUser.getAvatar();
        this.fromSchool = fromUser.getSchool();
        this.fromCreditScore = fromUser.getCreditScore();
        this.message = req.getMessage();
        this.createTime = req.getCreateTime() != null ? req.getCreateTime().toString() : "";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFromUserId() { return fromUserId; }
    public void setFromUserId(Long fromUserId) { this.fromUserId = fromUserId; }

    public String getFromNickname() { return fromNickname; }
    public void setFromNickname(String fromNickname) { this.fromNickname = fromNickname; }

    public String getFromAvatar() { return fromAvatar; }
    public void setFromAvatar(String fromAvatar) { this.fromAvatar = fromAvatar; }

    public String getFromSchool() { return fromSchool; }
    public void setFromSchool(String fromSchool) { this.fromSchool = fromSchool; }

    public Integer getFromCreditScore() { return fromCreditScore; }
    public void setFromCreditScore(Integer fromCreditScore) { this.fromCreditScore = fromCreditScore; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
