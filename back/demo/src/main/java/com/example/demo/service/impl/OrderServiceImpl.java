package com.example.demo.service.impl;

import com.example.demo.entity.Order;
import com.example.demo.entity.Product;
import com.example.demo.entity.User;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.DistributedLockService;
import com.example.demo.service.OrderService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final int DETAIL_TTL = 5;
    private static final int LIST_TTL = 2;
    private static final int LOCK_EXPIRE_SECONDS = 10;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private DistributedLockService lockService;

    // IdempotencyService 幂等 token 基础服务已实现
    // 完整接入需 Controller 层在请求中接收客户端传递的 token，
    // 典型用法：IdempotencyService.consumeToken("order:pay", orderId, token)

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    @Transactional
    public Order create(Long buyerId, Order order) {
        String orderNo = generateOrderNo();
        order.setOrderNo(orderNo);
        order.setBuyerId(buyerId);
        order.setStatus("待付款");
        order.setQuantity(1);
        order.setTotalAmount(order.getPrice());

        if ("商品".equals(order.getType()) && order.getProductId() != null) {
            Product product = productMapper.findById(order.getProductId());
            if (product == null) {
                throw new RuntimeException("商品不存在");
            }
            if (product.getSellerId().equals(buyerId)) {
                throw new RuntimeException("不能购买自己的商品");
            }
            order.setPrice(product.getPrice());
            order.setTotalAmount(product.getPrice());
            order.setSellerId(product.getSellerId());
            productMapper.updateStatus(product.getId(), "已预订");
        }

        if ("服务".equals(order.getType()) && order.getServiceId() != null) {
            Product service = productMapper.findById(order.getServiceId());
            if (service == null) {
                throw new RuntimeException("服务不存在");
            }
            if (service.getSellerId().equals(buyerId)) {
                throw new RuntimeException("不能预约自己的服务");
            }
            order.setPrice(service.getPrice());
            order.setTotalAmount(service.getPrice());
            order.setSellerId(service.getSellerId());
            productMapper.updateStatus(service.getId(), "已预订");
        }

        orderMapper.insert(order);

        evictListCache(buyerId);
        if (order.getSellerId() != null) {
            evictListCache(order.getSellerId());
        }

        return order;
    }

    @Override
    public Order getById(Long id) {
        String key = "order:" + id;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                Order order = objectMapper.readValue(cached, Order.class);
                enrichOrder(order);
                return order;
            }
        } catch (Exception e) {
            log.error("Redis GET 失败, key={}", key, e);
        }
        Order order = orderMapper.findById(id);
        if (order != null) {
            enrichOrder(order);
            try {
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(order), DETAIL_TTL, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.error("Redis SET 失败, key={}", key, e);
            }
        }
        return order;
    }

    @Override
    public Order getByOrderNo(String orderNo) {
        Order order = orderMapper.findByOrderNo(orderNo);
        if (order != null) {
            enrichOrder(order);
        }
        return order;
    }

    @Override
    public List<Order> list(Long userId, String status, String type, String role, Integer page, Integer pageSize) {
        String key = buildListCacheKey(userId, status, type, role, page, pageSize);
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<Order>>() {});
            }
        } catch (Exception e) {
            log.error("Redis GET 失败, key={}", key, e);
        }
        Integer offset = (page - 1) * pageSize;
        List<Order> orders = orderMapper.findList(userId, status, type, role, offset, pageSize);
        for (Order order : orders) {
            enrichOrder(order);
        }
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(orders), LIST_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Redis SET 失败, key={}", key, e);
        }
        return orders;
    }

    @Override
    public Long count(Long userId, String status, String type, String role) {
        return orderMapper.count(userId, status, type, role);
    }

    @Override
    @Transactional
    public void updateStatus(Long orderId, String status) {
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        orderMapper.updateStatus(orderId, status);

        evictDetailCache(orderId);
        evictListCache(order.getBuyerId());
        if (order.getSellerId() != null) {
            evictListCache(order.getSellerId());
        }
    }

    @Override
    @Transactional
    public void cancel(Long orderId, Long userId) {
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new RuntimeException("无权取消此订单");
        }
        if (!"待付款".equals(order.getStatus()) && !"待发货".equals(order.getStatus())) {
            throw new RuntimeException("当前状态不允许取消");
        }

        String lockKey = "order:cancel:" + orderId;
        boolean locked = lockService.executeWithLock(lockKey, LOCK_EXPIRE_SECONDS, () -> {
            Order recheck = orderMapper.findById(orderId);
            if (recheck == null || (!"待付款".equals(recheck.getStatus()) && !"待发货".equals(recheck.getStatus()))) {
                return;
            }

            if ("待发货".equals(recheck.getStatus())) {
                refundBalance(recheck.getBuyerId(), recheck.getTotalAmount());
            }
            restoreProductStatus(recheck);
            orderMapper.updateStatus(orderId, "已取消");
        });

        if (!locked) {
            throw new RuntimeException("操作太频繁，请稍后重试");
        }

        evictDetailCache(orderId);
        evictListCache(order.getBuyerId());
        if (order.getSellerId() != null) {
            evictListCache(order.getSellerId());
        }
    }

    @Override
    @Transactional
    public void ship(Long orderId, Long userId) {
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getSellerId().equals(userId)) {
            throw new RuntimeException("只有卖家可以发货");
        }
        if (!"待发货".equals(order.getStatus())) {
            throw new RuntimeException("当前状态不允许发货");
        }
        orderMapper.updateStatus(orderId, "待收货");

        evictDetailCache(orderId);
        evictListCache(order.getBuyerId());
        if (order.getSellerId() != null) {
            evictListCache(order.getSellerId());
        }
    }

    @Override
    @Transactional
    public void confirmReceive(Long orderId, Long userId) {
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getBuyerId().equals(userId)) {
            throw new RuntimeException("只有买家可以确认收货");
        }
        if (!"待收货".equals(order.getStatus())) {
            throw new RuntimeException("当前状态不允许确认收货");
        }

        String lockKey = "order:confirm:" + orderId;
        boolean locked = lockService.executeWithLock(lockKey, LOCK_EXPIRE_SECONDS, () -> {
            Order recheck = orderMapper.findById(orderId);
            if (recheck == null || !"待收货".equals(recheck.getStatus())) {
                return;
            }

            transferToSeller(recheck.getSellerId(), recheck.getTotalAmount());
            markOrderCompleted(recheck);
            orderMapper.updateStatus(orderId, "已完成");
        });

        if (!locked) {
            throw new RuntimeException("操作太频繁，请稍后重试");
        }

        evictDetailCache(orderId);
        evictListCache(order.getBuyerId());
        if (order.getSellerId() != null) {
            evictListCache(order.getSellerId());
        }
    }

    @Override
    @Transactional
    public void pay(Long orderId, Long userId) {
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getBuyerId().equals(userId)) {
            throw new RuntimeException("只有买家可以支付");
        }
        if (!"待付款".equals(order.getStatus())) {
            throw new RuntimeException("当前状态不允许支付");
        }

        String lockKey = "order:pay:" + userId;
        boolean locked = lockService.executeWithLock(lockKey, LOCK_EXPIRE_SECONDS, () -> {
            Order recheck = orderMapper.findById(orderId);
            if (recheck == null || !"待付款".equals(recheck.getStatus())) {
                return;
            }

            deductBalance(userId, recheck.getTotalAmount());
            orderMapper.updateStatus(orderId, "待发货");
        });

        if (!locked) {
            throw new RuntimeException("操作太频繁，请稍后重试");
        }

        evictDetailCache(orderId);
        evictListCache(order.getBuyerId());
        if (order.getSellerId() != null) {
            evictListCache(order.getSellerId());
        }
    }

    @Override
    @Transactional
    public void delete(Long orderId, Long userId) {
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new RuntimeException("无权删除此订单");
        }
        orderMapper.deleteById(orderId);

        evictDetailCache(orderId);
        evictListCache(order.getBuyerId());
        if (order.getSellerId() != null) {
            evictListCache(order.getSellerId());
        }
    }

    @Override
    public List<Order> getByProductId(Long productId) {
        return orderMapper.findByProductId(productId);
    }

    private void enrichOrder(Order order) {
        if (order == null) return;
        if (order.getBuyerId() != null) {
            User buyer = userMapper.findById(order.getBuyerId());
            if (buyer != null) {
                order.setBuyer(buyer);
            }
        }
        if (order.getSellerId() != null) {
            User seller = userMapper.findById(order.getSellerId());
            if (seller != null) {
                order.setSeller(seller);
            }
        }
        if ("商品".equals(order.getType()) && order.getProductId() != null) {
            Product product = productMapper.findById(order.getProductId());
            if (product != null) {
                order.setProduct(product);
            }
        }
    }

    private void deductBalance(Long userId, BigDecimal amount) {
        User buyer = userMapper.findById(userId);
        if (buyer == null) {
            throw new RuntimeException("买家账户不存在");
        }
        if (buyer.getBalance() == null || buyer.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("余额不足，无法支付");
        }
        BigDecimal newBalance = buyer.getBalance().subtract(amount);
        userMapper.updateBalance(userId, newBalance);
    }

    private void refundBalance(Long userId, BigDecimal amount) {
        User buyer = userMapper.findById(userId);
        if (buyer != null) {
            BigDecimal currentBalance = buyer.getBalance() != null ? buyer.getBalance() : BigDecimal.ZERO;
            BigDecimal newBalance = currentBalance.add(amount);
            userMapper.updateBalance(userId, newBalance);
        }
    }

    private void transferToSeller(Long sellerId, BigDecimal amount) {
        User seller = userMapper.findById(sellerId);
        if (seller != null) {
            BigDecimal currentBalance = seller.getBalance() != null ? seller.getBalance() : BigDecimal.ZERO;
            BigDecimal newBalance = currentBalance.add(amount);
            userMapper.updateBalance(sellerId, newBalance);
        }
    }

    private void markOrderCompleted(Order order) {
        if ("商品".equals(order.getType()) && order.getProductId() != null) {
            productMapper.updateStatus(order.getProductId(), "已售出");
        }
        if ("服务".equals(order.getType()) && order.getServiceId() != null) {
            productMapper.updateStatus(order.getServiceId(), "已完成");
        }
    }

    private void restoreProductStatus(Order order) {
        if ("商品".equals(order.getType()) && order.getProductId() != null) {
            productMapper.updateStatus(order.getProductId(), "在售");
        }
        if ("服务".equals(order.getType()) && order.getServiceId() != null) {
            productMapper.updateStatus(order.getServiceId(), "可用");
        }
    }

    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = (int) (Math.random() * 10000);
        return "ORD" + timestamp + String.format("%04d", random);
    }

    private void evictDetailCache(Long orderId) {
        try {
            redisTemplate.delete("order:" + orderId);
        } catch (Exception e) {
            log.error("删除订单缓存失败, id={}", orderId, e);
        }
    }

    private void evictListCache(Long userId) {
        try {
            Set<String> keys = redisTemplate.keys("order:list:" + userId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.error("删除用户订单列表缓存失败, userId={}", userId, e);
        }
    }

    private String buildListCacheKey(Long userId, String status, String type, String role, Integer page, Integer pageSize) {
        return String.format("order:list:%d:%s:%s:%s:%d:%d",
                userId, nvl(status), nvl(type), nvl(role), page, pageSize);
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }
}
