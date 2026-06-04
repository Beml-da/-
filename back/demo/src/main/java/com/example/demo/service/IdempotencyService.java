package com.example.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 幂等性 token 服务
 * 用于防止订单创建、支付等操作的重复提交
 * 客户端先调用 generateToken() 获取 token，再在请求中携带 token
 */
@Service
public class IdempotencyService {

    private static final String TOKEN_PREFIX = "idempotency:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 生成幂等 token
     *
     * @param businessKey 业务标识，如 "order:create"、"order:pay"
     * @param bizId       业务ID，如订单ID
     * @param ttl         token有效期（秒）
     * @return 幂等 token
     */
    public String generateToken(String businessKey, String bizId, long ttl) {
        String token = businessKey + ":" + bizId + ":" + System.currentTimeMillis() + ":" + (int) (Math.random() * 100000);
        String key = TOKEN_PREFIX + businessKey + ":" + bizId;
        redisTemplate.opsForValue().set(key, token, ttl, TimeUnit.SECONDS);
        return token;
    }

    /**
     * 校验并消费 token（只允许使用一次）
     *
     * @param businessKey 业务标识
     * @param bizId       业务ID
     * @param token       客户端提交的 token
     * @return true=通过（首次消费），false=重复请求
     */
    public boolean consumeToken(String businessKey, String bizId, String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        String key = TOKEN_PREFIX + businessKey + ":" + bizId;
        String stored = redisTemplate.opsForValue().get(key);
        if (token.equals(stored)) {
            redisTemplate.delete(key);
            return true;
        }
        return false;
    }

    /**
     * 校验 token 是否存在（不消费，用于查询类操作）
     *
     * @param businessKey 业务标识
     * @param bizId       业务ID
     * @param token       客户端提交的 token
     * @return true=有效，false=无效或已过期
     */
    public boolean validateToken(String businessKey, String bizId, String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        String key = TOKEN_PREFIX + businessKey + ":" + bizId;
        String stored = redisTemplate.opsForValue().get(key);
        return token.equals(stored);
    }
}
