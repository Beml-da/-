package com.example.demo.controller;

import com.example.demo.annotation.RateLimit;
import com.example.demo.common.Result;
import com.example.demo.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 相关接口。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    /**
     * AI 生成商品描述文案。
     * POST /api/ai/generate-description
     * Body: { "title": "商品名称", "category": "商品分类（可空）" }
     * 返回: { description, priceSuggestion }
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

        try {
            Map<String, String> data = aiService.generateProductDescription(title, category);
            return Result.success(data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(500, "AI 生成失败: " + e.getMessage());
        }
    }
}
