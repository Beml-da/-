package com.example.demo.controller;

import com.example.demo.common.JwtUtil;
import com.example.demo.common.Result;
import com.example.demo.entity.Order;
import com.example.demo.entity.Product;
import com.example.demo.entity.User;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     * 获取订单列表
     */
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String role,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        try {
            Long userId = extractUserId(token);
            List<Order> orders = orderService.list(userId, status, type, role, page, pageSize);
            Long total = orderService.count(userId, status, type, role);

            // 转换订单数据
            List<Map<String, Object>> orderList = new ArrayList<>();
            for (Order order : orders) {
                orderList.add(convertOrderToMap(order));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("list", orderList);
            result.put("total", total);
            result.put("page", page);
            result.put("pageSize", pageSize);

            return Result.success(result);
        } catch (Exception e) {
            return Result.error(401, e.getMessage());
        }
    }

    /**
     * 获取订单详情
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id) {
        try {
            Long userId = extractUserId(token);
            Order order = orderService.getById(id);
            if (order == null) {
                return Result.error(404, "订单不存在");
            }
            return Result.success(convertOrderToMap(order));
        } catch (Exception e) {
            return Result.error(401, e.getMessage());
        }
    }

    /**
     * 创建订单
     */
    @PostMapping
    public Result<Map<String, Object>> create(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String, Object> request) {
        try {
            Long buyerId = extractUserId(token);

            Order order = new Order();
            order.setType((String) request.get("type"));
            order.setProductId(request.get("productId") != null ? ((Number) request.get("productId")).longValue() : null);
            order.setServiceId(request.get("serviceId") != null ? ((Number) request.get("serviceId")).longValue() : null);
            order.setContact((String) request.get("contact"));
            order.setRemark((String) request.get("remark"));

            // 获取商品/服务信息设置价格和卖家
            if ("商品".equals(order.getType()) && order.getProductId() != null) {
                Product product = productMapper.findById(order.getProductId());
                if (product == null) {
                    return Result.error(400, "商品不存在");
                }
                order.setPrice(product.getPrice());
                order.setTotalAmount(product.getPrice());
                order.setSellerId(product.getSellerId());
            }

            Order createdOrder = orderService.create(buyerId, order);
            return Result.success(convertOrderToMap(createdOrder), "订单创建成功");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 更新订单状态
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        try {
            Long userId = extractUserId(token);
            String status = request.get("status");
            orderService.updateStatus(id, status);
            return Result.success(null, "状态更新成功");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 取消订单
     */
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> request) {
        try {
            Long userId = extractUserId(token);
            orderService.cancel(id, userId);
            return Result.success(null, "订单已取消");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 确认发货（卖家）
     */
    @PutMapping("/{id}/ship")
    public Result<Void> ship(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id) {
        try {
            Long userId = extractUserId(token);
            orderService.ship(id, userId);
            return Result.success(null, "已确认发货");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 确认收货（买家）
     */
    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id) {
        try {
            Long userId = extractUserId(token);
            orderService.confirmReceive(id, userId);
            return Result.success(null, "已确认收货，交易完成");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 支付订单
     */
    @PutMapping("/{id}/pay")
    public Result<Void> pay(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id) {
        try {
            Long userId = extractUserId(token);
            orderService.pay(id, userId);
            return Result.success(null, "支付成功");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 删除订单
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id) {
        try {
            Long userId = extractUserId(token);
            orderService.delete(id, userId);
            return Result.success(null, "订单已删除");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 从Token中提取用户ID
     */
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
     * 将Order对象转换为Map
     */
    private Map<String, Object> convertOrderToMap(Order order) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", order.getId());
        map.put("orderNo", order.getOrderNo());
        map.put("type", order.getType());
        map.put("productId", order.getProductId());
        map.put("serviceId", order.getServiceId());
        map.put("buyerId", order.getBuyerId());
        map.put("sellerId", order.getSellerId());
        map.put("price", order.getPrice());
        map.put("quantity", order.getQuantity());
        map.put("totalAmount", order.getTotalAmount());
        map.put("status", order.getStatus());
        map.put("contact", order.getContact());
        map.put("remark", order.getRemark());
        map.put("createTime", order.getCreateTime());
        map.put("updateTime", order.getUpdateTime());

        // 转换买家信息
        if (order.getBuyer() != null) {
            map.put("buyer", convertUserToMap(order.getBuyer()));
        }

        // 转换卖家信息
        if (order.getSeller() != null) {
            map.put("seller", convertUserToMap(order.getSeller()));
        }

        // 转换商品信息
        if (order.getProduct() != null) {
            map.put("product", convertProductToMap(order.getProduct()));
        }

        return map;
    }

    /**
     * 将User对象转换为Map
     */
    private Map<String, Object> convertUserToMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("avatar", user.getAvatar());
        map.put("phone", user.getPhone());
        map.put("creditScore", user.getCreditScore());
        map.put("level", user.getLevel());
        map.put("isVerified", user.getIsVerified() != null && user.getIsVerified() == 1);
        map.put("createTime", user.getCreateTime());
        return map;
    }

    /**
     * 将Product对象转换为Map
     */
    private Map<String, Object> convertProductToMap(Product product) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", product.getId());
        map.put("title", product.getTitle());
        map.put("description", product.getDescription());
        map.put("price", product.getPrice());
        map.put("originalPrice", product.getOriginalPrice());
        map.put("status", product.getStatus());
        map.put("condition", product.getCondition());
        map.put("location", product.getLocation());
        map.put("createTime", product.getCreateTime());

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
}
