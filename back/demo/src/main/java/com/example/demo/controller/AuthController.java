package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.dto.RegisterResponse;
import com.example.demo.entity.User;
import com.example.demo.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 用户名密码登录
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            return Result.success(response);
        } catch (Exception e) {
            return Result.error(401, e.getMessage());
        }
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        try {
            RegisterResponse response = authService.register(request);
            return Result.success(response);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 手机验证码登录
     */
    @PostMapping("/login/sms")
    public Result<LoginResponse> loginBySms(@RequestParam String phone, @RequestParam String code) {
        try {
            LoginResponse response = authService.loginBySms(phone, code);
            return Result.success(response);
        } catch (Exception e) {
            return Result.error(401, e.getMessage());
        }
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/current")
    public Result<User> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String token) {
        try {
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            User user = authService.getCurrentUser(token);
            return Result.success(user);
        } catch (Exception e) {
            return Result.error(401, e.getMessage());
        }
    }

    /**
     * 更新个人资料
     */
    @PutMapping("/profile")
    public Result<User> updateProfile(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String, Object> params) {
        try {
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            User currentUser = authService.getCurrentUser(token);

            // 构建更新对象
            User updateUser = new User();
            updateUser.setId(currentUser.getId());
            if (params.containsKey("nickname")) {
                updateUser.setNickname((String) params.get("nickname"));
            }
            if (params.containsKey("phone")) {
                updateUser.setPhone((String) params.get("phone"));
            }
            if (params.containsKey("email")) {
                updateUser.setEmail((String) params.get("email"));
            }

            authService.updateProfile(updateUser);
            User updated = authService.getCurrentUser(token);
            return Result.success(updated);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        authService.logout(token);
        return Result.success();
    }
}
