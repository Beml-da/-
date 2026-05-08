package com.example.demo.service.impl;

import com.example.demo.common.JwtUtil;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.dto.RegisterResponse;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;

    private static final String SECRET_KEY = "jiaoyihang-trade-system-secret-key-2024-very-long-and-secure";
    private static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000;

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.findByUsername(request.getUsername());
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        if (!user.getPassword().equals(request.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        if (user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用，请联系管理员");
        }

        String token = generateToken(user);
        return buildLoginResponse(user, token);
    }

    @Override
    public LoginResponse loginBySms(String phone, String code) {
        User user = userMapper.findByPhone(phone);
        if (user == null) {
            throw new RuntimeException("该手机号未注册");
        }

        if (!"123456".equals(code)) {
            throw new RuntimeException("验证码错误");
        }

        if (user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用，请联系管理员");
        }

        String token = generateToken(user);
        return buildLoginResponse(user, token);
    }

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // 1. 检查用户名是否已被注册
        if (userMapper.findByUsername(request.getUsername()) != null) {
            throw new RuntimeException("用户名已被注册");
        }

        // 2. 检查手机号是否已被注册（如果提供了手机号）
        if (request.getPhone() != null && !request.getPhone().isEmpty()) {
            if (userMapper.findByPhone(request.getPhone()) != null) {
                throw new RuntimeException("该手机号已被注册");
            }
        }

        // 3. 构建用户对象
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setNickname(request.getNickname() != null && !request.getNickname().isEmpty()
                ? request.getNickname()
                : request.getUsername());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setStatus(1);
        user.setDeleted(0);

        // 4. 插入数据库
        int rows = userMapper.insert(user);
        if (rows <= 0) {
            throw new RuntimeException("注册失败，请稍后重试");
        }

        // 5. 生成 Token 并返回
        String token = generateToken(user);
        RegisterResponse.UserInfo userInfo = RegisterResponse.UserInfo.fromUser(user);
        return new RegisterResponse(token, userInfo);
    }

    @Override
    public User getCurrentUser(String token) {
        if (token == null || token.isEmpty()) {
            throw new RuntimeException("未登录");
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        Long userId = JwtUtil.getUserIdFromToken(token);
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return user;
    }

    @Override
    public void updateProfile(User user) {
        if (user.getId() == null) {
            throw new RuntimeException("用户ID不能为空");
        }
        userMapper.update(user);
    }

    @Override
    public void logout(String token) {
        // 如果使用 Redis 存储 Token，可以在这里删除
    }

    private String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("nickname", user.getNickname());

        return Jwts.builder()
                .claims(claims)
                .subject(user.getId().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private LoginResponse buildLoginResponse(User user, String token) {
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getPhone(),
                user.getEmail()
        );
        return new LoginResponse(token, userInfo);
    }
}
