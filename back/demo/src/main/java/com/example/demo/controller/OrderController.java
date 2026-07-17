package com.example.demo.controller;

import com.example.demo.annotation.RateLimit;
import com.example.demo.common.JwtUtil;
import com.example.demo.common.Result;
import com.example.demo.entity.Order;
import com.example.demo.entity.Product;
import com.example.demo.entity.Refund;
import com.example.demo.entity.User;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.IdempotencyService;
import com.example.demo.service.OrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired
    private IdempotencyService idempotencyService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 幂等 token 有效期：5 分钟。客户端在 create/pay 前先调
     * GET /api/orders/idempotency-token?biz=create 拿到 token 后带回。
     */
    private static final long IDEMPOTENCY_TTL_SECONDS = 300;

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
     * 幂等：客户端必须先调 GET /api/orders/idempotency-token?biz=create 拿到 token，并在请求体中传 idempotencyToken。
     */
    @RateLimit(keyType = RateLimit.KeyType.USER, count = 10, window = 60, message = "下单过于频繁，请稍后再试")
    @PostMapping
    public Result<Map<String, Object>> create(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String, Object> request) {
        try {
            Long buyerId = extractUserId(token);

            String idempotencyToken = request.get("idempotencyToken") == null
                    ? null : String.valueOf(request.get("idempotencyToken"));
            if (idempotencyToken == null || idempotencyToken.isEmpty()) {
                return Result.error(400, "缺少幂等参数 idempotencyToken");
            }
            String bizId = "u" + buyerId;
            if (!idempotencyService.consumeToken("order:create", bizId, idempotencyToken)) {
                return Result.error(409, "请勿重复提交订单");
            }

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
     * 客户端调用此接口获取下单/支付的幂等 token。
     * GET /api/orders/idempotency-token?biz=create|pay
     */
    @GetMapping("/idempotency-token")
    public Result<Map<String, Object>> issueIdempotencyToken(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(defaultValue = "create") String biz) {
        try {
            Long userId = extractUserId(token);
            String bizId = "u" + userId;
            String t = idempotencyService.generateToken("order:" + biz, bizId, IDEMPOTENCY_TTL_SECONDS);
            Map<String, Object> data = new HashMap<>();
            data.put("idempotencyToken", t);
            data.put("biz", biz);
            data.put("ttlSeconds", IDEMPOTENCY_TTL_SECONDS);
            return Result.success(data);
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
     * 支付订单（幂等：依赖订单自身的状态机 + 分布式锁；如客户端传 idempotencyToken 也会消费）
     */
    @PutMapping("/{id}/pay")
    public Result<Void> pay(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> request) {
        try {
            Long userId = extractUserId(token);
            if (request != null && request.get("idempotencyToken") != null) {
                String bizId = "u" + userId + "-order-" + id;
                if (!idempotencyService.consumeToken("order:pay", bizId, request.get("idempotencyToken"))) {
                    return Result.error(409, "请勿重复支付");
                }
            }
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
     * 申请退款（买家）
     */
    @PutMapping("/{id}/refund/apply")
    public Result<Map<String, Object>> applyRefund(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        try {
            Long userId = extractUserId(token);
            String reason = request != null ? request.get("reason") : null;
            Refund refund = orderService.applyRefund(id, userId, reason);
            return Result.success(refundToMap(refund), "退款申请已提交，等待卖家处理");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 同意退款（卖家）
     */
    @PutMapping("/{id}/refund/approve")
    public Result<Void> approveRefund(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id) {
        try {
            Long userId = extractUserId(token);
            orderService.approveRefund(id, userId);
            return Result.success(null, "已同意退款，金额已退回买家账户");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 拒绝退款（卖家）
     */
    @PutMapping("/{id}/refund/reject")
    public Result<Void> rejectRefund(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        try {
            Long userId = extractUserId(token);
            String rejectReason = request != null ? request.get("rejectReason") : null;
            orderService.rejectRefund(id, userId, rejectReason);
            return Result.success(null, "已拒绝退款申请");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 获取订单最新的退款记录
     */
    @GetMapping("/{id}/refund")
    public Result<Map<String, Object>> getLatestRefund(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id) {
        try {
            Refund refund = orderService.getLatestRefund(id);
            if (refund == null) {
                return Result.success(null);
            }
            return Result.success(refundToMap(refund));
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
        map.put("refundStatus", order.getRefundStatus());
        map.put("refundReason", order.getRefundReason());
        map.put("refundTime", order.getRefundTime());

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
     * 将Refund对象转换为Map
     */
    private Map<String, Object> refundToMap(Refund refund) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", refund.getId());
        map.put("refundNo", refund.getRefundNo());
        map.put("orderId", refund.getOrderId());
        map.put("orderNo", refund.getOrderNo());
        map.put("applicantId", refund.getApplicantId());
        map.put("sellerId", refund.getSellerId());
        map.put("amount", refund.getAmount());
        map.put("reason", refund.getReason());
        map.put("status", refund.getStatus());
        map.put("rejectReason", refund.getRejectReason());
        map.put("processedTime", refund.getProcessedTime());
        map.put("createTime", refund.getCreateTime());
        return map;
    }

    /**
     * 使用 Jackson 解析 JSON 数组字符串。
     * 兼容 [\"url1\",\"url2\"]、["url1", "url2"]、空字符串、null 等情况。
     */
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            // 兼容历史脏数据：解析失败返回空数组，由上层空状态兜底
            return new ArrayList<>();
        }
    }
}
