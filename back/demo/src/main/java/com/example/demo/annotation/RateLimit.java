package com.example.demo.annotation;

import java.lang.annotation.*;

/**
 * 分布式限流注解
 * 支持按用户 ID 或 IP 进行接口级别的限流
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流 key 的前缀，配合 key() 使用
     */
    String prefix() default "rate:limit:";

    /**
     * 限流 key 的生成规则
     * user   - 按用户 ID 限流（需登录）
     * ip     - 按 IP 限流（无需登录）
     * uri    - 按请求 URI 限流（全局共享）
     */
    KeyType keyType() default KeyType.USER;

    /**
     * 时间窗口内允许的最大请求数
     */
    int count() default 10;

    /**
     * 时间窗口大小（秒）
     */
    int window() default 60;

    /**
     * 限流触发后返回的错误消息
     */
    String message() default "请求过于频繁，请稍后再试";

    enum KeyType {
        USER,  // 按登录用户 ID
        IP,    // 按请求 IP
        URI    // 按请求 URI（全局共享额度）
    }
}
