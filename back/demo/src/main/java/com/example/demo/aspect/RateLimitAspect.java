package com.example.demo.aspect;

import com.example.demo.annotation.RateLimit;
import com.example.demo.common.Result;
import com.example.demo.service.RateLimitService;
import com.example.demo.service.impl.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 限流 AOP 切面
 * 在带有 @RateLimit 注解的方法执行前进行分布式限流检查
 */
@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final RateLimitService rateLimitService;

    public RateLimitAspect(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Around("@annotation(com.example.demo.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        String identifier = resolveIdentifier(rateLimit);
        String key = rateLimitService.buildKey(rateLimit, identifier);

        boolean allowed = rateLimitService.tryAcquire(key, rateLimit.count(), rateLimit.window());

        if (!allowed) {
            log.warn("[RateLimit] 限流触发 | key={} | count={} | window={}s | message={}",
                    key, rateLimit.count(), rateLimit.window(), rateLimit.message());

            HttpServletRequest request = getRequest();
            if (request != null) {
                log.warn("[RateLimit] 触发来源 | IP={} | URI={} | Method={}",
                        getClientIp(request), request.getRequestURI(), request.getMethod());
            }

            return Result.error(429, rateLimit.message());
        }

        log.debug("[RateLimit] 通过 | key={} | current={}",
                key, rateLimitService.getCurrentCount(key));

        return joinPoint.proceed();
    }

    /**
     * 根据 keyType 解析限流标识符
     */
    private String resolveIdentifier(RateLimit rateLimit) {
        switch (rateLimit.keyType()) {
            case USER: {
                Long userId = UserContext.getCurrentUserId();
                if (userId != null) {
                    return "user:" + userId;
                }
                String ip = getRequestIpOrDefault();
                return "ip:" + ip;
            }
            case IP:
                return "ip:" + getRequestIpOrDefault();
            case URI:
                HttpServletRequest request = getRequest();
                if (request != null) {
                    return "uri:" + request.getRequestURI();
                }
                return "uri:unknown";
            default:
                return "unknown";
        }
    }

    private String getRequestIpOrDefault() {
        HttpServletRequest request = getRequest();
        if (request != null) {
            return getClientIp(request);
        }
        return "no-request";
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * 获取真实客户端 IP（支持代理）
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }
}
