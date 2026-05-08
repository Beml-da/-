package com.example.demo.controller;

import com.example.demo.common.JwtUtil;
import com.example.demo.common.Result;
import com.example.demo.dto.ProductRequest;
import com.example.demo.entity.Product;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private UserMapper userMapper;

    /**
     * 发布商品
     */
    @PostMapping
    public Result<Product> publish(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody ProductRequest request) {
        try {
            Long userId = extractUserId(token);
            Product product = productService.publish(userId, request);
            return Result.success(product, "发布成功");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 获取商品列表（分页）
     */
    @GetMapping
    public Result<List<Product>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false, defaultValue = "newest") String sortBy,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        List<Product> list = productService.list(keyword, category, condition, minPrice, maxPrice, sortBy, page, pageSize);
        Long total = productService.count(keyword, category, condition, minPrice, maxPrice);
        return Result.success(list, total, page, pageSize);
    }

    /**
     * 获取商品详情
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Product product = productService.getById(id);
        if (product == null) {
            return Result.error(404, "商品不存在");
        }
        productService.incrementViewCount(id);

        Map<String, Object> result = new HashMap<>();
        result.put("id", product.getId());
        result.put("title", product.getTitle());
        result.put("description", product.getDescription());
        result.put("price", product.getPrice());
        result.put("originalPrice", product.getOriginalPrice());
        result.put("viewCount", product.getViewCount() != null ? product.getViewCount() : 0);
        result.put("favoriteCount", product.getFavoriteCount() != null ? product.getFavoriteCount() : 0);
        result.put("condition", product.getCondition());
        result.put("status", product.getStatus());
        result.put("location", product.getLocation());
        result.put("isNegotiable", product.getIsNegotiable());
        result.put("subCategory", product.getSubCategory());
        result.put("createTime", product.getCreateTime());
        result.put("updateTime", product.getUpdateTime());

        // 解析 images JSON
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            result.put("images", parseJsonArray(product.getImages()));
        } else {
            result.put("images", Collections.emptyList());
        }

        // 解析 tags JSON
        if (product.getTags() != null && !product.getTags().isEmpty()) {
            result.put("tags", parseJsonArray(product.getTags()));
        } else {
            result.put("tags", Collections.emptyList());
        }

        // 加载卖家信息
        if (product.getSellerId() != null) {
            User seller = userMapper.findById(product.getSellerId());
            if (seller != null) {
                Map<String, Object> sellerInfo = new HashMap<>();
                sellerInfo.put("id", seller.getId());
                sellerInfo.put("username", seller.getUsername());
                sellerInfo.put("nickname", seller.getNickname());
                sellerInfo.put("avatar", seller.getAvatar());
                sellerInfo.put("creditScore", seller.getCreditScore());
                sellerInfo.put("level", seller.getLevel());
                sellerInfo.put("isVerified", seller.getIsVerified() != null && seller.getIsVerified() == 1);
                sellerInfo.put("createTime", seller.getCreateTime());
                result.put("sellerId", seller.getId());
                result.put("seller", sellerInfo);
            }
        }

        // 分类名称
        result.put("categoryId", product.getCategoryId());
        result.put("categoryName", product.getCategoryId());
        result.put("type", product.getType());
        result.put("serviceType", product.getServiceType());
        result.put("priceUnit", product.getPriceUnit());

        return Result.success(result);
    }

    /**
     * 热门商品
     */
    @GetMapping("/hot")
    public Result<List<Map<String, Object>>> hot(@RequestParam(required = false, defaultValue = "10") Integer limit) {
        List<Product> products = productService.getHotProducts(limit);
        List<Map<String, Object>> result = products.stream().map(this::parseProduct).toList();
        return Result.success(result);
    }

    /**
     * 初始化浏览量（生成模拟真实浏览数据，仅首次或数据为空时调用）
     */
    @PostMapping("/init-views")
    public Result<Void> initViewCounts() {
        productService.initViewCounts();
        return Result.success(null, "浏览量初始化成功");
    }

    /**
     * 搜索建议（按商品名称去重）
     */
    @GetMapping("/suggestions")
    public Result<List<Map<String, Object>>> suggestions(@RequestParam String keyword,
                                                         @RequestParam(required = false, defaultValue = "10") Integer limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Result.success(java.util.Collections.emptyList());
        }
        List<Map<String, Object>> suggestions = productService.searchSuggestions(keyword.trim(), limit);
        return Result.success(suggestions);
    }

    /**
     * 统一搜索（商品+服务）
     */
    @GetMapping("/search")
    public Result<List<Map<String, Object>>> search(
            @RequestParam String keyword,
            @RequestParam(required = false, defaultValue = "newest") String sortBy,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer pageSize) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Result.success(java.util.Collections.emptyList(), 0L, 1, 0);
        }
        List<Product> list = productService.searchAll(keyword.trim(), sortBy, page, pageSize);
        Long total = productService.countSearchAll(keyword.trim());
        List<Map<String, Object>> result = list.stream().map(this::parseProduct).toList();
        return Result.success(result, total, page, pageSize);
    }

    /**
     * 最新商品
     */
    @GetMapping("/newest")
    public Result<List<Map<String, Object>>> newest(@RequestParam(required = false, defaultValue = "10") Integer limit) {
        List<Product> products = productService.getNewestProducts(limit);
        List<Map<String, Object>> result = products.stream().map(this::parseProduct).toList();
        return Result.success(result);
    }

    /**
     * 最新发布（仅商品）
     */
    @GetMapping("/newest-all")
    public Result<List<Map<String, Object>>> newestAll(@RequestParam(required = false, defaultValue = "10") Integer limit) {
        List<Product> products = productService.getNewestAllProducts(limit);
        List<Map<String, Object>> result = products.stream().map(this::parseProduct).toList();
        return Result.success(result);
    }

    /**
     * 将 Product 转换为 Map，解析 images 和 tags
     */
    private Map<String, Object> parseProduct(Product product) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", product.getId());
        map.put("title", product.getTitle());
        map.put("description", product.getDescription());
        map.put("price", product.getPrice());
        map.put("originalPrice", product.getOriginalPrice());
        map.put("condition", product.getCondition());
        map.put("status", product.getStatus());
        map.put("location", product.getLocation());
        map.put("viewCount", product.getViewCount() != null ? product.getViewCount() : 0);
        map.put("favoriteCount", product.getFavoriteCount() != null ? product.getFavoriteCount() : 0);
        map.put("createTime", product.getCreateTime());
        map.put("type", product.getType());
        map.put("serviceType", product.getServiceType());
        map.put("priceUnit", product.getPriceUnit());
        map.put("sellerId", product.getSellerId());
        map.put("categoryId", product.getCategoryId());

        // 解析 images JSON
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            map.put("images", parseJsonArray(product.getImages()));
        } else {
            map.put("images", Collections.emptyList());
        }

        // 解析 tags JSON
        if (product.getTags() != null && !product.getTags().isEmpty()) {
            map.put("tags", parseJsonArray(product.getTags()));
        } else {
            map.put("tags", Collections.emptyList());
        }

        return map;
    }

    /**
     * 获取卖家商品列表
     */
    @GetMapping("/seller/{sellerId}")
    public Result<List<Product>> sellerProducts(@PathVariable Long sellerId) {
        return Result.success(productService.getBySellerId(sellerId));
    }

    /**
     * 获取我的发布
     */
    @GetMapping("/my")
    public Result<List<Product>> myProducts(
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            Long userId = extractUserId(token);
            return Result.success(productService.getBySellerId(userId));
        } catch (Exception e) {
            return Result.error(401, e.getMessage());
        }
    }

    /**
     * 获取我的商品
     */
    @GetMapping("/my/product")
    public Result<List<Product>> myItems(
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            Long userId = extractUserId(token);
            return Result.success(productService.getBySellerIdAndType(userId, "product"));
        } catch (Exception e) {
            return Result.error(401, e.getMessage());
        }
    }

    /**
     * 获取我的服务
     */
    @GetMapping("/my/service")
    public Result<List<Product>> myServices(
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            Long userId = extractUserId(token);
            return Result.success(productService.getBySellerIdAndType(userId, "service"));
        } catch (Exception e) {
            return Result.error(401, e.getMessage());
        }
    }

    /**
     * 更新商品
     */
    @PutMapping("/{id}")
    public Result<Product> update(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody ProductRequest request) {
        try {
            Long userId = extractUserId(token);
            Product product = productService.update(id, userId, request);
            return Result.success(product, "更新成功");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 更新商品状态
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody java.util.Map<String, String> body) {
        try {
            Long userId = extractUserId(token);
            productService.updateStatus(id, userId, body.get("status"));
            return Result.success(null, "状态更新成功");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 删除商品
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            Long userId = extractUserId(token);
            productService.delete(id, userId);
            return Result.success(null, "删除成功");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
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

    /**
     * 解析 JSON 数组字符串，如 ["a","b","c"]
     */
    private List<String> parseJsonArray(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return result;
        json = json.trim();
        if (json.startsWith("[") && json.endsWith("]")) {
            json = json.substring(1, json.length() - 1);
        }
        if (json.isEmpty()) return result;
        boolean inQuote = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
            } else if (c == ',' && !inQuote) {
                result.add(sb.toString().trim());
                sb = new StringBuilder();
            } else {
                sb.append(c);
            }
        }
        String last = sb.toString().trim();
        if (!last.isEmpty()) {
            result.add(last);
        }
        return result;
    }
}
