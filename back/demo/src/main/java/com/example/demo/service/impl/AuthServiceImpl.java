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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String SECRET_KEY = "jiaoyihang-trade-system-secret-key-2024-very-long-and-secure";
    private static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000;
    private static final long USER_CACHE_TTL = 1 * 60 * 60;
    private static final long SMS_CODE_TTL = 5;
    private static final long SMS_SEND_INTERVAL = 60;

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
        cacheUserSession(user);
        return buildLoginResponse(user, token);
    }

    @Override
    public LoginResponse loginBySms(String phone, String code) {
        User user = userMapper.findByPhone(phone);
        if (user == null) {
            throw new RuntimeException("该手机号未注册");
        }

        if (!verifySmsCode(phone, code)) {
            throw new RuntimeException("验证码错误");
        }

        if (user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用，请联系管理员");
        }

        String token = generateToken(user);
        cacheUserSession(user);
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
        user.setBalance(new BigDecimal("1000.00"));

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

        Map<Object, Object> cached = redisTemplate.opsForHash().entries("user:session:" + userId);
        if (cached != null && !cached.isEmpty()) {
            User user = new User();
            user.setId(userId);
            user.setUsername((String) cached.get("username"));
            user.setNickname((String) cached.get("nickname"));
            user.setPhone((String) cached.get("phone"));
            user.setEmail((String) cached.get("email"));
            user.setAvatar((String) cached.get("avatar"));
            String balance = (String) cached.get("balance");
            if (balance != null && !balance.isEmpty()) {
                user.setBalance(new BigDecimal(balance));
            }
            String status = (String) cached.get("status");
            if (status != null && !status.isEmpty()) {
                user.setStatus(Integer.parseInt(status));
            }
            return user;
        }

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
        redisTemplate.delete("user:session:" + user.getId());
    }

    @Override
    public void logout(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        try {
            long remaining = System.currentTimeMillis() + 1000;
            try {
                var claims = Jwts.parser()
                        .verifyWith(Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8)))
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
                remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
            } catch (Exception ignored) {
            }
            if (remaining > 0) {
                redisTemplate.opsForValue().set("blacklist:" + token, "1", remaining, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void sendSmsCode(String phone) {
        if (phone == null || phone.isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new RuntimeException("手机号格式不正确");
        }

        String intervalKey = "sms:interval:" + phone;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(intervalKey))) {
            throw new RuntimeException("发送太频繁，请" + SMS_SEND_INTERVAL + "秒后重试");
        }

        String code = String.format("%06d", new java.util.Random().nextInt(999999));
        String codeKey = "sms:code:" + phone;
        redisTemplate.opsForValue().set(codeKey, code, SMS_CODE_TTL, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(intervalKey, "1", SMS_SEND_INTERVAL, TimeUnit.SECONDS);

        System.out.println("[SMS] 发送验证码到 " + phone + "，验证码: " + code);
    }

    @Override
    public boolean verifySmsCode(String phone, String code) {
        if (phone == null || code == null) {
            return false;
        }
        String codeKey = "sms:code:" + phone;
        String stored = redisTemplate.opsForValue().get(codeKey);
        if (stored != null && stored.equals(code)) {
            redisTemplate.delete(codeKey);
            return true;
        }
        return false;
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
                user.getEmail(),
                user.getAvatar(),
                user.getBalance()
        );
        return new LoginResponse(token, userInfo);
    }

    private void cacheUserSession(User user) {
        try {
            Map<String, String> userMap = new HashMap<>();
            userMap.put("id", user.getId() != null ? user.getId().toString() : "");
            userMap.put("username", user.getUsername() != null ? user.getUsername() : "");
            userMap.put("nickname", user.getNickname() != null ? user.getNickname() : "");
            userMap.put("phone", user.getPhone() != null ? user.getPhone() : "");
            userMap.put("email", user.getEmail() != null ? user.getEmail() : "");
            userMap.put("avatar", user.getAvatar() != null ? user.getAvatar() : "");
            userMap.put("balance", user.getBalance() != null ? user.getBalance().toString() : "");
            userMap.put("status", user.getStatus() != null ? user.getStatus().toString() : "1");
            redisTemplate.opsForHash().putAll("user:session:" + user.getId(), userMap);
            redisTemplate.expire("user:session:" + user.getId(), USER_CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void refreshUserCache(Long userId) {
        User user = userMapper.findById(userId);
        if (user != null) {
            cacheUserSession(user);
        }
    }
}
