package com.example.demo.controller;

import com.example.demo.common.JwtUtil;
import com.example.demo.common.Result;
import com.example.demo.entity.Favorite;
import com.example.demo.entity.Product;
import com.example.demo.mapper.FavoriteMapper;
import com.example.demo.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Autowired
    private ProductMapper productMapper;

    /**
     * 获取我的收藏列表
     */
    @GetMapping
    public Result<List<Map<String, Object>>> list(
            @RequestHeader(value = "Authorization", required = false) String token) {
        Long userId = extractUserId(token);
        List<Favorite> favorites = favoriteMapper.findByUserId(userId);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Favorite f : favorites) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", f.getId());
            map.put("userId", f.getUserId());
            map.put("productId", f.getProductId());
            map.put("createTime", f.getCreateTime());

            // 关联商品信息
            if (f.getProductId() != null) {
                Product p = productMapper.findById(f.getProductId());
                if (p != null) {
                    Map<String, Object> productMap = new HashMap<>();
                    productMap.put("id", p.getId());
                    productMap.put("title", p.getTitle());
                    productMap.put("price", p.getPrice());
                    productMap.put("images", parseJsonArray(p.getImages()));
                    productMap.put("status", p.getStatus());
                    productMap.put("type", p.getType());
                    productMap.put("favoriteCount", p.getFavoriteCount());
                    map.put("product", productMap);
                }
            }
            list.add(map);
        }
        return Result.success(list);
    }

    /**
     * 添加收藏
     */
    @PostMapping
    public Result<Map<String, Object>> add(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String, Object> body) {
        Long userId = extractUserId(token);
        Long productId = body.get("productId") != null
                ? ((Number) body.get("productId")).longValue() : null;

        if (productId == null) {
            return Result.error(400, "商品ID不能为空");
        }

        if (favoriteMapper.exists(userId, productId) > 0) {
            return Result.error(409, "已收藏过该商品");
        }

        Favorite f = new Favorite();
        f.setUserId(userId);
        f.setProductId(productId);
        favoriteMapper.insert(f);

        // 更新商品收藏数
        productMapper.incrementFavoriteCount(productId);

        Map<String, Object> data = new HashMap<>();
        data.put("id", f.getId());
        data.put("productId", productId);
        return Result.success(data, "收藏成功");
    }

    /**
     * 取消收藏
     */
    @DeleteMapping("/{productId}")
    public Result<Void> remove(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long productId) {
        Long userId = extractUserId(token);

        int rows = favoriteMapper.deleteByUserAndProduct(userId, productId);
        if (rows == 0) {
            return Result.error(404, "未收藏该商品");
        }

        // 更新商品收藏数
        productMapper.decrementFavoriteCount(productId);

        return Result.success(null, "已取消收藏");
    }

    /**
     * 检查是否已收藏
     */
    @GetMapping("/check/{productId}")
    public Result<Map<String, Object>> check(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long productId) {
        Long userId = extractUserId(token);
        boolean favorited = favoriteMapper.exists(userId, productId) > 0;
        Map<String, Object> data = new HashMap<>();
        data.put("isFavorited", favorited);
        return Result.success(data);
    }

    private Long extractUserId(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isEmpty()) {
            throw new RuntimeException("请先登录");
        }
        Long userId = JwtUtil.getUserIdFromToken(token);
        if (userId == null) {
            throw new RuntimeException("登录已过期，请重新登录");
        }
        return userId;
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            return om.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
