package com.example.demo.controller;

import com.example.demo.common.JwtUtil;
import com.example.demo.common.Result;
import com.example.demo.dto.ServiceRequest;
import com.example.demo.entity.Product;
import com.example.demo.service.ServiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    @Autowired
    private ServiceService serviceService;

    /**
     * 发布服务
     */
    @PostMapping
    public Result<Product> publish(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody ServiceRequest request) {
        try {
            Long userId = extractUserId(token);
            Product service = serviceService.publish(userId, request);
            return Result.success(service, "发布成功");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 获取服务列表（分页）
     */
    @GetMapping
    public Result<List<Map<String, Object>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String serviceType,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        List<Product> list = serviceService.list(keyword, serviceType, page, pageSize);
        Long total = serviceService.countByKeyword(keyword, serviceType);
        List<Map<String, Object>> result = list.stream().map(this::parseService).toList();
        return Result.success(result, total, page, pageSize);
    }

    /**
     * 将 Product 转换为 Map，解析 images 和 tags
     */
    private Map<String, Object> parseService(Product service) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", service.getId());
        map.put("title", service.getTitle());
        map.put("description", service.getDescription());
        map.put("price", service.getPrice());
        map.put("location", service.getLocation());
        map.put("status", service.getStatus());
        map.put("serviceType", service.getServiceType());
        map.put("priceUnit", service.getPriceUnit());
        map.put("sellerId", service.getSellerId());
        map.put("rating", service.getRating() != null ? service.getRating() : 5.0);
        map.put("ratingCount", service.getRatingCount() != null ? service.getRatingCount() : 0);
        map.put("orderCount", service.getOrderCount() != null ? service.getOrderCount() : 0);
        map.put("createTime", service.getCreateTime());

        // 解析 tags JSON
        if (service.getTags() != null && !service.getTags().isEmpty()) {
            map.put("tags", parseJsonArray(service.getTags()));
        } else {
            map.put("tags", Collections.emptyList());
        }

        // 解析 images JSON
        if (service.getImages() != null && !service.getImages().isEmpty()) {
            map.put("images", parseJsonArray(service.getImages()));
        } else {
            map.put("images", Collections.emptyList());
        }

        return map;
    }

    /**
     * 解析 JSON 数组字符串
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

    /**
     * 获取服务详情
     */
    @GetMapping("/{id}")
    public Result<Product> detail(@PathVariable Long id) {
        Product service = serviceService.getById(id);
        if (service == null) {
            return Result.error(404, "服务不存在");
        }
        return Result.success(service);
    }

    /**
     * 获取我的服务
     */
    @GetMapping("/my")
    public Result<List<Product>> myServices(
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            Long userId = extractUserId(token);
            return Result.success(serviceService.getByProviderId(userId));
        } catch (Exception e) {
            return Result.error(401, e.getMessage());
        }
    }

    /**
     * 更新服务
     */
    @PutMapping("/{id}")
    public Result<Product> update(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody ServiceRequest request) {
        try {
            Long userId = extractUserId(token);
            Product service = serviceService.update(id, userId, request);
            return Result.success(service, "更新成功");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 删除服务
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            Long userId = extractUserId(token);
            serviceService.delete(id, userId);
            return Result.success(null, "删除成功");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 更新服务状态
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody java.util.Map<String, String> body) {
        try {
            Long userId = extractUserId(token);
            String status = body.get("status");
            if (status == null || status.trim().isEmpty()) {
                return Result.error(400, "状态不能为空");
            }
            serviceService.updateStatus(id, userId, status);
            return Result.success(null, "状态更新成功");
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
}
