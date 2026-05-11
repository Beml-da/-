package com.example.demo.dto;

import com.example.demo.entity.User;

public class FriendVO {
    private Long id;
    private String nickname;
    private String avatar;
    private String school;
    private Integer creditScore;
    private Boolean online;

    public FriendVO() {}

    public FriendVO(User user) {
        this.id = user.getId();
        this.nickname = user.getNickname();
        this.avatar = user.getAvatar();
        this.school = user.getSchool();
        this.creditScore = user.getCreditScore();
        this.online = false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getSchool() { return school; }
    public void setSchool(String school) { this.school = school; }

    public Integer getCreditScore() { return creditScore; }
    public void setCreditScore(Integer creditScore) { this.creditScore = creditScore; }

    public Boolean getOnline() { return online; }
    public void setOnline(Boolean online) { this.online = online; }
}
