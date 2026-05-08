package com.example.demo.dto;

import com.example.demo.entity.User;

public class RegisterResponse {

    private String token;
    private UserInfo user;

    public RegisterResponse() {}

    public RegisterResponse(String token, UserInfo user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserInfo getUser() {
        return user;
    }

    public void setUser(UserInfo user) {
        this.user = user;
    }

    public static class UserInfo {
        private Long id;
        private String username;
        private String nickname;
        private String phone;
        private String email;

        public UserInfo() {}

        public UserInfo(Long id, String username, String nickname, String phone, String email) {
            this.id = id;
            this.username = username;
            this.nickname = nickname;
            this.phone = phone;
            this.email = email;
        }

        public static UserInfo fromUser(User user) {
            return new UserInfo(
                    user.getId(),
                    user.getUsername(),
                    user.getNickname(),
                    user.getPhone(),
                    user.getEmail()
            );
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
}
