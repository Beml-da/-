package com.example.demo.service;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.dto.RegisterResponse;
import com.example.demo.entity.User;

public interface AuthService {

    /**
     * 用户名密码登录
     */
    LoginResponse login(LoginRequest request);

    /**
     * 手机验证码登录
     */
    LoginResponse loginBySms(String phone, String code);

    /**
     * 用户注册（注册成功后自动登录，返回 Token）
     */
    RegisterResponse register(RegisterRequest request);

    /**
     * 获取当前登录用户信息
     */
    User getCurrentUser(String token);

    /**
     * 更新个人资料
     */
    void updateProfile(User user);

    /**
     * 退出登录
     */
    void logout(String token);
}
