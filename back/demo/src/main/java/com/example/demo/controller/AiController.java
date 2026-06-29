package com.example.demo.controller;

import com.example.demo.annotation.RateLimit;
import com.example.demo.common.Result;
import com.example.demo.service.AiException;
import com.example.demo.service.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 相关接口。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    @Autowired
    private AiService aiService;

    /**
     * AI 生成商品描述文案。
     * POST /api/ai/generate-description
     * Body: { "title": "商品名称", "category": "商品分类（可空）" }
     * 成功: 200 { description, priceSuggestion }
     * 错误:
     *   400 参数错; 401 鉴权错; 402 余额不足; 429 限流;
     *   502 上游错误; 504 超时; 500 其他
     */
    @RateLimit(keyType = RateLimit.KeyType.USER, count = 20, window = 60, message = "AI 生成过于频繁，请稍后再试")
    @PostMapping("/generate-description")
    public Result<Map<String, String>> generateDescription(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String, Object> body) {

        Object titleObj = body.get("title");
        String title = titleObj == null ? "" : titleObj.toString().trim();
        Object categoryObj = body.get("category");
        String category = categoryObj == null ? "" : categoryObj.toString().trim();

        if (title.isEmpty()) {
            return Result.error(400, "商品名称不能为空");
        }
        if (title.length() > 80) {
            return Result.error(400, "商品名称过长，请控制在 80 字以内");
        }

        try {
            Map<String, String> data = aiService.generateProductDescription(title, category);
            return Result.success(data);
        } catch (AiException e) {
            log.warn("[AiController] AI 业务异常 | kind={} | message={}",
                    e.getKind(), e.getMessage());
            int code = switch (e.getKind()) {
                case AUTH_INVALID -> 401;
                case INSUFFICIENT_BALANCE -> 402;
                case RATE_LIMIT -> 429;
                case UPSTREAM_ERROR -> 502;
                case TIMEOUT -> 504;
                case BAD_REQUEST -> 400;
                default -> 500;
            };
            return Result.error(code, e.getMessage());
        } catch (Exception e) {
            log.error("[AiController] 兜底异常: {}", e.getMessage(), e);
            return Result.error(500, "AI 服务异常，请稍后重试");
        }
    }
}
