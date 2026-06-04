package com.example.demo.service;

import com.example.demo.annotation.RateLimit;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 分布式限流服务
 * 基于 Redis + Lua 滑动窗口算法实现
 */
@Service
public class RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${rate-limit.enabled:true}")
    private boolean enabled;

    private DefaultRedisScript<List> rateLimitScript;

    public RateLimitService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @PostConstruct
    public void init() {
        rateLimitScript = new DefaultRedisScript<>();
        rateLimitScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("script/rate_limit.lua"))
        );
        rateLimitScript.setResultType(List.class);
    }

    /**
     * 执行限流检查
     *
     * @param key    Redis key
     * @param count  最大请求数
     * @param window 时间窗口（秒）
     * @return true=允许，false=拒绝
     */
    public boolean tryAcquire(String key, int count, int window) {
        if (!enabled) {
            return true;
        }

        long now = System.currentTimeMillis();

        List<String> keys = Arrays.asList(key);
        List<Long> result = stringRedisTemplate.execute(
                rateLimitScript,
                keys,
                String.valueOf(window),
                String.valueOf(count),
                String.valueOf(now)
        );

        if (result == null || result.isEmpty()) {
            return true;
        }

        return result.get(0) == 1L;
    }

    /**
     * 根据限流注解配置生成 Redis key
     */
    public String buildKey(RateLimit rateLimit, String identifier) {
        return rateLimit.prefix() + identifier;
    }

    /**
     * 获取当前窗口内的请求数（用于日志记录）
     */
    public long getCurrentCount(String key) {
        Long size = stringRedisTemplate.opsForZSet().zCard(key);
        return size != null ? size : 0;
    }
}
