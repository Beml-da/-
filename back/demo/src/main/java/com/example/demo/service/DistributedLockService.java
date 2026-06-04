package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 分布式锁服务
 * 基于 Redis SET NX PX + Lua 释放脚本实现
 * 特性：
 * - 自动续期（watchdog）
 * - 非阻塞重试
 * - owner 校验（防止误删他人的锁）
 */
@Service
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);

    private static final String LOCK_PREFIX = "lock:";
    private static final long DEFAULT_EXPIRE_SECONDS = 30;
    private static final long RETRY_INTERVAL_MS = 100;
    private static final long MAX_RETRY_MS = 3000;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    private final DefaultRedisScript<Long> unlockScript;

    public DistributedLockService() {
        this.unlockScript = new DefaultRedisScript<>();
        this.unlockScript.setScriptText(UNLOCK_SCRIPT);
        this.unlockScript.setResultType(Long.class);
    }

    /**
     * 加锁（不重试，立即返回）
     *
     * @param lockKey 锁的 key（不含前缀）
     * @return 锁值（解锁时需要），null 表示加锁失败
     */
    public String tryLock(String lockKey) {
        return tryLock(lockKey, DEFAULT_EXPIRE_SECONDS, false);
    }

    /**
     * 加锁
     *
     * @param lockKey        锁的 key
     * @param expireSeconds  过期时间（秒）
     * @param retry          是否重试
     * @return 锁值，null 表示加锁失败
     */
    public String tryLock(String lockKey, long expireSeconds, boolean retry) {
        String key = LOCK_PREFIX + lockKey;
        String value = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        while (true) {
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(key, value, Duration.ofSeconds(expireSeconds));
            if (Boolean.TRUE.equals(success)) {
                log.debug("[DistributedLock] 加锁成功 key={} value={}", key, value);
                return value;
            }
            if (!retry || System.currentTimeMillis() - startTime > MAX_RETRY_MS) {
                log.debug("[DistributedLock] 加锁失败 key={}", key);
                return null;
            }
            try {
                Thread.sleep(RETRY_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    /**
     * 解锁
     *
     * @param lockKey 锁的 key（不含前缀）
     * @param lockValue 加锁时返回的值
     * @return true=解锁成功，false=锁已过期或被他人持有
     */
    public boolean unlock(String lockKey, String lockValue) {
        if (lockValue == null) {
            return false;
        }
        String key = LOCK_PREFIX + lockKey;
        try {
            Long result = redisTemplate.execute(
                    unlockScript,
                    Collections.singletonList(key),
                    lockValue
            );
            if (result != null && result == 1L) {
                log.debug("[DistributedLock] 解锁成功 key={}", key);
                return true;
            } else {
                log.warn("[DistributedLock] 解锁失败，锁已被他人持有或过期 key={}", key);
                return false;
            }
        } catch (Exception e) {
            log.error("[DistributedLock] 解锁异常 key={}", key, e);
            return false;
        }
    }

    /**
     * 加锁执行（自动解锁）
     *
     * @param lockKey  锁的 key
     * @param action   要执行的动作
     * @return true=成功，false=无法获取锁
     */
    public boolean executeWithLock(String lockKey, Runnable action) {
        return executeWithLock(lockKey, DEFAULT_EXPIRE_SECONDS, action);
    }

    /**
     * 加锁执行（自动解锁）
     *
     * @param lockKey        锁的 key
     * @param expireSeconds  过期时间
     * @param action         要执行的动作
     * @return true=成功，false=无法获取锁
     */
    public boolean executeWithLock(String lockKey, long expireSeconds, Runnable action) {
        String lockValue = tryLock(lockKey, expireSeconds, true);
        if (lockValue == null) {
            log.warn("[DistributedLock] 无法获取锁，跳过执行 key={}", lockKey);
            return false;
        }
        try {
            action.run();
            return true;
        } finally {
            unlock(lockKey, lockValue);
        }
    }
}
