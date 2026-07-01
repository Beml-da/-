package com.example.demo.controller;

import com.example.demo.annotation.RateLimit;
import com.example.demo.common.Result;
import com.example.demo.service.AiException;
import com.example.demo.ai.CustomerServiceAiService;
import com.example.demo.service.impl.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 客服接口。
 * POST /api/ai/customer-service/ask   - 提问
 * POST /api/ai/customer-service/reset - 清除上下文
 * GET  /api/ai/customer-service/sources - 查看检索来源（调试用）
 */
@RestController
@RequestMapping("/api/ai/customer-service")
public class CustomerServiceAiController {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceAiController.class);

    @Autowired
    private CustomerServiceAiService customerServiceAiService;

    /**
     * POST /api/ai/customer-service/ask
     * Body: { "question": "string" }
     * Header: Authorization: Bearer <JWT>
     */
    @RateLimit(keyType = RateLimit.KeyType.USER, count = 30, window = 60,
               message = "AI 客服调用过于频繁，请稍后再试")
    @PostMapping("/ask")
    public Result<Map<String, Object>> ask(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String, Object> body) {

        // 鉴权：复用现有 UserContext
        Long userId;
        try {
            userId = UserContext.getCurrentUserId();
        } catch (Exception e) {
            return Result.error(401, "请先登录后再使用 AI 客服");
        }

        Object questionObj = body.get("question");
        if (questionObj == null) {
            return Result.error(400, "question 参数不能为空");
        }
        String question = questionObj.toString().trim();
        if (question.isEmpty()) {
            return Result.error(400, "问题内容不能为空");
        }
        if (question.length() > 500) {
            return Result.error(400, "问题过长，请控制在 500 字以内");
        }

        try {
            CustomerServiceAiService.CustomerAnswer answer =
                    customerServiceAiService.answer(userId, question);

            return Result.success(Map.of(
                    "answer", answer.answer(),
                    "context", answer.context() != null ? answer.context() : ""
            ));
        } catch (AiException e) {
            log.warn("[CustomerServiceAiCtrl] AI 业务异常 | kind={} | message={}",
                    e.getKind(), e.getMessage());
            int code = switch (e.getKind()) {
                case AUTH_INVALID -> 401;
                case INSUFFICIENT_BALANCE -> 402;
                case RATE_LIMIT -> 429;
                case UPSTREAM_ERROR -> 502;
                case TIMEOUT -> 504;
                default -> 500;
            };
            return Result.error(code, e.getMessage());
        } catch (Exception e) {
            log.error("[CustomerServiceAiCtrl] 兜底异常: {}", e.getMessage(), e);
            return Result.error(500, "AI 客服服务异常，请稍后重试");
        }
    }

    /**
     * POST /api/ai/customer-service/reset
     * 清除当前用户的对话上下文。
     */
    @RateLimit(keyType = RateLimit.KeyType.USER, count = 5, window = 60,
               message = "重置过于频繁")
    @PostMapping("/reset")
    public Result<String> reset(
            @RequestHeader(value = "Authorization", required = false) String token) {
        Long userId;
        try {
            userId = UserContext.getCurrentUserId();
        } catch (Exception e) {
            return Result.error(401, "请先登录");
        }
        customerServiceAiService.clearContext(userId);
        return Result.success("上下文已清除");
    }
}
