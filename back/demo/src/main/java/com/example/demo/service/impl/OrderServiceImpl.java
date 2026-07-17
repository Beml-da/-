package com.example.demo.service.impl;

import com.example.demo.common.BizException;
import com.example.demo.entity.Order;
import com.example.demo.entity.Product;
import com.example.demo.entity.Refund;
import com.example.demo.entity.User;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.mapper.RefundMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.AuthService;
import com.example.demo.service.DistributedLockService;
import com.example.demo.service.OrderService;
import com.example.demo.service.ProductService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private RefundMapper refundMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private DistributedLockService lockService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ProductService productService;

    @Autowired
    private AuthService authService;

    // IdempotencyService 幂等 token 基础服务已实现
    // 完整接入需 Controller 层在请求中接收客户端传递的 token，
    // 典型用法：IdempotencyService.consumeToken("order:pay", orderId, token)

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
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
                throw new BizException("商品不存在");
            }
            if (product.getSellerId().equals(buyerId)) {
                throw new BizException("不能购买自己的商品");
            }
            // 只有"在售"的商品才能下单，防止已被预订/已售出的商品被重复购买
            if (!"在售".equals(product.getStatus())) {
                throw new BizException("商品当前不可购买（状态：" + product.getStatus() + "）");
            }
            order.setPrice(product.getPrice());
            order.setTotalAmount(product.getPrice());
            order.setSellerId(product.getSellerId());

            // CAS：仅当 status 仍是"在售"时才改为"已预订"，避免并发重复下单
            // 一键购买：在同一事务内完成扣款+变更状态
            Integer locked = transactionTemplate.execute(status -> {
                // 1) 检查并锁定商品
                int rows = productMapper.updateStatusCAS(product.getId(), "在售", "已预订");
                if (rows == 0) {
                    throw new BizException("商品已被别人下单，请选择其他商品");
                }
                // 2) 扣减买家余额（乐观 SQL，余额不足返回 0）
                int deductRows = userMapper.deductBalance(buyerId, product.getPrice());
                if (deductRows == 0) {
                    // 扣款失败，回滚商品状态
                    productMapper.updateStatusCAS(product.getId(), "已预订", "在售");
                    throw new BizException("余额不足，无法购买");
                }
                // 3) 立即给卖家加款（一手交钱一手交货）
                userMapper.addBalance(product.getSellerId(), product.getPrice());
                // 4) 插入订单（状态直接设为待发货，模拟支付成功）
                order.setStatus("待发货");
                orderMapper.insert(order);
                orderMapper.updatePaidTime(order.getId());
                return 1;
            });
            if (locked == null) {
                throw new BizException("下单失败，请稍后重试");
            }
        } else if ("服务".equals(order.getType()) && order.getServiceId() != null) {
            Product service = productMapper.findById(order.getServiceId());
            if (service == null) {
                throw new BizException("服务不存在");
            }
            if (service.getSellerId().equals(buyerId)) {
                throw new BizException("不能预约自己的服务");
            }
            if (!"可用".equals(service.getStatus())) {
                throw new BizException("服务当前不可预约（状态：" + service.getStatus() + "）");
            }
            order.setPrice(service.getPrice());
            order.setTotalAmount(service.getPrice());
            order.setSellerId(service.getSellerId());

            Integer lockedSvc = transactionTemplate.execute(status -> {
                int rows = productMapper.updateStatusCAS(service.getId(), "可用", "已预订");
                if (rows == 0) {
                    throw new BizException("服务已被预约，请选择其他服务");
                }
                // 扣减买家余额
                int deductRows = userMapper.deductBalance(buyerId, service.getPrice());
                if (deductRows == 0) {
                    productMapper.updateStatusCAS(service.getId(), "已预订", "可用");
                    throw new BizException("余额不足，无法预约");
                }
                // 立即给卖家加款
                userMapper.addBalance(service.getSellerId(), service.getPrice());
                order.setStatus("待发货");
                orderMapper.insert(order);
                orderMapper.updatePaidTime(order.getId());
                return 1;
            });
            if (lockedSvc == null) {
                throw new BizException("预约失败，请稍后重试");
            }
        } else {
            throw new BizException("订单类型必须指定商品或服务");
        }

        evictListCache(buyerId);
        if (order.getSellerId() != null) {
            evictListCache(order.getSellerId());
        }
        evictProductCache(order.getProductId(), order.getServiceId());

        // 清除商品相关缓存，确保列表页立即反映最新状态
        productService.evictAllCaches();

        // 刷新买家和卖家的 Redis 缓存（余额变更后必须）
        authService.refreshUserCache(buyerId);
        if (order.getSellerId() != null) {
            authService.refreshUserCache(order.getSellerId());
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
            throw new BizException("订单不存在");
        }
        orderMapper.updateStatus(orderId, status);

        evictDetailCache(orderId);
        evictListCache(order.getBuyerId());
        if (order.getSellerId() != null) {
            evictListCache(order.getSellerId());
        }
    }

    @Override
    public void cancel(Long orderId, Long userId) {
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new BizException("无权取消此订单");
        }
        if (!"待付款".equals(order.getStatus()) && !"待发货".equals(order.getStatus())) {
            throw new BizException("当前状态不允许取消");
        }

        String lockKey = "order:cancel:" + orderId;
        boolean locked = lockService.executeWithLock(lockKey, LOCK_EXPIRE_SECONDS, () -> {
            transactionTemplate.execute(tx -> {
                Order recheck = orderMapper.findById(orderId);
                if (recheck == null
                        || (!"待付款".equals(recheck.getStatus())
                            && !"待发货".equals(recheck.getStatus()))) {
                    return 1;
                }

                // 恢复商品状态为"在售" / "可用"
                if ("商品".equals(recheck.getType()) && recheck.getProductId() != null) {
                    productMapper.updateStatusCAS(recheck.getProductId(), "已预订", "在售");
                } else if ("服务".equals(recheck.getType()) && recheck.getServiceId() != null) {
                    productMapper.updateStatusCAS(recheck.getServiceId(), "已预订", "可用");
                }

                // 待发货说明买家已付款，需要把钱退回买家，同时扣回卖家的钱
                if ("待发货".equals(recheck.getStatus())) {
                    // 扣回卖家已收的钱
                    userMapper.deductBalance(recheck.getSellerId(), recheck.getTotalAmount());
                    // 退款给买家
                    int addRows = userMapper.addBalance(recheck.getBuyerId(), recheck.getTotalAmount());
                    if (addRows == 0) {
                        throw new BizException("退款失败：买家账户异常");
                    }
                }

                orderMapper.updateStatus(orderId, "已取消");
                return 1;
            });
        });

        if (!locked) {
            throw new BizException("操作太频繁，请稍后重试");
        }

        evictDetailCache(orderId);
        evictListCache(order.getBuyerId());
        if (order.getSellerId() != null) {
            evictListCache(order.getSellerId());
        }
        evictProductCache(order.getProductId(), order.getServiceId());
        productService.evictAllCaches();

        // 取消订单时刷新买家和卖家余额缓存
        authService.refreshUserCache(order.getBuyerId());
        if (order.getSellerId() != null) {
            authService.refreshUserCache(order.getSellerId());
        }
    }

    @Override
    @Transactional
    public void ship(Long orderId, Long userId) {
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!order.getSellerId().equals(userId)) {
            throw new BizException("只有卖家可以发货");
        }
        if (!"待发货".equals(order.getStatus())) {
            throw new BizException("当前状态不允许发货");
        }

        String lockKey = "order:ship:" + orderId;
        boolean locked = lockService.executeWithLock(lockKey, LOCK_EXPIRE_SECONDS, () -> {
            transactionTemplate.execute(tx -> {
                Order recheck = orderMapper.findById(orderId);
                if (recheck == null || !"待发货".equals(recheck.getStatus())) {
                    return 1;
                }
                orderMapper.updateStatus(orderId, "待收货");
                orderMapper.updateShippedTime(orderId);
                return 1;
            });
        });

        if (!locked) {
            throw new BizException("操作太频繁，请稍后重试");
        }

        evictDetailCache(orderId);
        evictListCache(order.getBuyerId());
        if (order.getSellerId() != null) {
            evictListCache(order.getSellerId());
        }
        evictProductCache(order.getProductId(), order.getServiceId());
        productService.evictAllCaches();
    }

    @Override
    public void confirmReceive(Long orderId, Long userId) {
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!order.getBuyerId().equals(userId)) {
            throw new BizException("只有买家可以确认收货");
        }
        if (!"待收货".equals(order.getStatus())) {
            throw new BizException("当前状态不允许确认收货");
        }

        String lockKey = "order:confirm:" + orderId;
        boolean locked = lockService.executeWithLock(lockKey, LOCK_EXPIRE_SECONDS, () -> {
            transactionTemplate.execute(tx -> {
                Order recheck = orderMapper.findById(orderId);
                if (recheck == null || !"待收货".equals(recheck.getStatus())) {
                    return 1;
                }

                // 卖家加款已在 create() 时完成（create 时买家付款同时给卖家加款）
                // 此处只需标记商品状态为已售出
                if ("商品".equals(recheck.getType()) && recheck.getProductId() != null) {
                    int rows = productMapper.updateStatusCAS(recheck.getProductId(), "已预订", "已售出");
                    if (rows == 0) {
                        log.warn("商品 {} 状态非已预订，无法标记为已售出", recheck.getProductId());
                    }
                } else if ("服务".equals(recheck.getType()) && recheck.getServiceId() != null) {
                    int rows = productMapper.updateStatusCAS(recheck.getServiceId(), "已预订", "已完成");
                    if (rows == 0) {
                        log.warn("服务 {} 状态非已预订，无法标记为已完成", recheck.getServiceId());
                    }
                }

                // 订单状态改为"已完成"
                orderMapper.updateStatus(orderId, "已完成");
                return 1;
            });
        });

        if (!locked) {
            throw new BizException("操作太频繁，请稍后重试");
        }

        evictDetailCache(orderId);
        evictListCache(order.getBuyerId());
        if (order.getSellerId() != null) {
            evictListCache(order.getSellerId());
        }
        evictProductCache(order.getProductId(), order.getServiceId());
        productService.evictAllCaches();

        // 确认收货时刷新买家和卖家余额缓存
        authService.refreshUserCache(order.getBuyerId());
        if (order.getSellerId() != null) {
            authService.refreshUserCache(order.getSellerId());
        }
    }

    @Override
    public void pay(Long orderId, Long userId) {
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!order.getBuyerId().equals(userId)) {
            throw new BizException("只有买家可以支付");
        }
        // 幂等：如果已是待发货/待收货/已完成，说明已支付过，直接返回
        if ("待发货".equals(order.getStatus()) || "待收货".equals(order.getStatus()) || "已完成".equals(order.getStatus())) {
            return;
        }
        if (!"待付款".equals(order.getStatus())) {
            throw new BizException("当前状态不允许支付");
        }

        // 用订单ID作为锁 key，避免同一订单并发支付；同时校验商品当前必须是"已预订"
        String lockKey = "order:pay:" + orderId;
        boolean locked = lockService.executeWithLock(lockKey, LOCK_EXPIRE_SECONDS, () -> {
            // 在事务内重新查询订单和商品
            transactionTemplate.execute(tx -> {
                Order recheck = orderMapper.findById(orderId);
                if (recheck == null) {
                    throw new BizException("订单不存在");
                }
                if (!"待付款".equals(recheck.getStatus())) {
                    throw new BizException("当前状态不允许支付");
                }

                // 校验商品仍处于"已预订"状态（防止商品被并发修改）
                if ("商品".equals(recheck.getType()) && recheck.getProductId() != null) {
                    Product product = productMapper.findById(recheck.getProductId());
                    if (product == null) {
                        throw new BizException("商品不存在");
                    }
                    if (!"已预订".equals(product.getStatus())) {
                        throw new BizException("商品状态异常（" + product.getStatus() + "），无法支付");
                    }
                } else if ("服务".equals(recheck.getType()) && recheck.getServiceId() != null) {
                    Product service = productMapper.findById(recheck.getServiceId());
                    if (service == null) {
                        throw new BizException("服务不存在");
                    }
                    if (!"已预订".equals(service.getStatus())) {
                        throw new BizException("服务状态异常（" + service.getStatus() + "），无法支付");
                    }
                }

                // 用乐观 SQL 扣减余额：影响行数=0 表示余额不足或用户不存在
                int deductRows = userMapper.deductBalance(userId, recheck.getTotalAmount());
                if (deductRows == 0) {
                    throw new BizException("余额不足，无法支付");
                }

                // 订单变更为"待发货"
                int orderRows = orderMapper.updateStatus(orderId, "待发货");
                if (orderRows == 0) {
                    throw new BizException("订单状态更新失败，请重试");
                }

                // 记录付款时间
                orderMapper.updatePaidTime(orderId);
                return 1;
            });
        });

        if (!locked) {
            throw new BizException("操作太频繁，请稍后重试");
        }

        evictDetailCache(orderId);
        evictListCache(order.getBuyerId());
        if (order.getSellerId() != null) {
            evictListCache(order.getSellerId());
        }
        evictProductCache(order.getProductId(), order.getServiceId());
        productService.evictAllCaches();
    }

    @Override
    @Transactional
    public void delete(Long orderId, Long userId) {
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new BizException("无权删除此订单");
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

    @Override
    public Refund applyRefund(Long orderId, Long userId, String reason) {
        if (reason == null || reason.trim().length() < 5) {
            throw new BizException("退款原因至少 5 个字");
        }
        if (reason.length() > 500) {
            throw new BizException("退款原因不能超过 500 字");
        }

        String lockKey = "order:refund:apply:" + orderId;
        Refund[] result = new Refund[1];
        boolean locked = lockService.executeWithLock(lockKey, LOCK_EXPIRE_SECONDS, () -> {
            Refund refund = transactionTemplate.execute(tx -> {
                Order order = orderMapper.findById(orderId);
                if (order == null) {
                    throw new BizException("订单不存在");
                }
                if (!order.getBuyerId().equals(userId)) {
                    throw new BizException("只有买家可以申请退款");
                }
                if (!"待发货".equals(order.getStatus()) && !"待收货".equals(order.getStatus())) {
                    throw new BizException("当前订单状态不允许申请退款");
                }
                Refund existing = refundMapper.findLatestByOrderId(orderId);
                if (existing != null && "退款中".equals(existing.getStatus())) {
                    throw new BizException("该订单已有进行中的退款申请");
                }

                Refund r = new Refund();
                r.setRefundNo(generateRefundNo());
                r.setOrderId(orderId);
                r.setOrderNo(order.getOrderNo());
                r.setApplicantId(userId);
                r.setSellerId(order.getSellerId());
                r.setAmount(order.getTotalAmount());
                r.setReason(reason.trim());
                r.setStatus("退款中");
                refundMapper.insert(r);

                order.setRefundStatus("退款中");
                order.setRefundReason(reason.trim());
                orderMapper.update(order);
                return r;
            });
            result[0] = refund;
        });

        if (!locked) {
            throw new BizException("操作太频繁，请稍后重试");
        }

        evictDetailCache(orderId);
        Order order = orderMapper.findById(orderId);
        if (order != null) {
            evictListCache(order.getBuyerId());
            if (order.getSellerId() != null) {
                evictListCache(order.getSellerId());
            }
        }
        productService.evictAllCaches();
        return result[0];
    }

    @Override
    public void approveRefund(Long orderId, Long userId) {
        String lockKey = "order:refund:approve:" + orderId;
        boolean locked = lockService.executeWithLock(lockKey, LOCK_EXPIRE_SECONDS, () -> {
            transactionTemplate.execute(tx -> {
                Order order = orderMapper.findById(orderId);
                if (order == null) {
                    throw new BizException("订单不存在");
                }
                if (!order.getSellerId().equals(userId)) {
                    throw new BizException("只有卖家可以处理退款");
                }
                if (!"退款中".equals(order.getStatus())) {
                    throw new BizException("订单未处于退款中状态");
                }
                Refund refund = refundMapper.findLatestByOrderId(orderId);
                if (refund == null || !"退款中".equals(refund.getStatus())) {
                    throw new BizException("没有可处理的退款申请");
                }

                // 用乐观 SQL 退钱给买家
                int rows = userMapper.addBalance(order.getBuyerId(), refund.getAmount());
                if (rows == 0) {
                    throw new BizException("退款失败：买家账户异常");
                }

                // 商品恢复在售
                if ("商品".equals(order.getType()) && order.getProductId() != null) {
                    productMapper.updateStatusCAS(order.getProductId(), "已预订", "在售");
                } else if ("服务".equals(order.getType()) && order.getServiceId() != null) {
                    productMapper.updateStatusCAS(order.getServiceId(), "已预订", "可用");
                }

                refundMapper.updateStatus(refund.getId(), "已退款", null);

                order.setRefundStatus("已退款");
                order.setRefundTime(LocalDateTime.now());
                order.setStatus("已退款");
                orderMapper.update(order);
                return 1;
            });
        });

        if (!locked) {
            throw new BizException("操作太频繁，请稍后重试");
        }

        evictDetailCache(orderId);
        Order order = orderMapper.findById(orderId);
        if (order != null) {
            evictListCache(order.getBuyerId());
            if (order.getSellerId() != null) {
                evictListCache(order.getSellerId());
            }
            evictProductCache(order.getProductId(), order.getServiceId());

            // 同意退款时刷新买家和卖家余额缓存
            authService.refreshUserCache(order.getBuyerId());
            authService.refreshUserCache(order.getSellerId());
        }
        productService.evictAllCaches();
    }

    @Override
    public void rejectRefund(Long orderId, Long userId, String rejectReason) {
        if (rejectReason == null || rejectReason.trim().isEmpty()) {
            throw new BizException("请填写拒绝原因");
        }
        if (rejectReason.length() > 500) {
            throw new BizException("拒绝原因不能超过 500 字");
        }

        String lockKey = "order:refund:reject:" + orderId;
        boolean locked = lockService.executeWithLock(lockKey, LOCK_EXPIRE_SECONDS, () -> {
            transactionTemplate.execute(tx -> {
                Order order = orderMapper.findById(orderId);
                if (order == null) {
                    throw new BizException("订单不存在");
                }
                if (!order.getSellerId().equals(userId)) {
                    throw new BizException("只有卖家可以处理退款");
                }
                if (!"退款中".equals(order.getStatus())) {
                    throw new BizException("订单未处于退款中状态");
                }
                Refund refund = refundMapper.findLatestByOrderId(orderId);
                if (refund == null || !"退款中".equals(refund.getStatus())) {
                    throw new BizException("没有可处理的退款申请");
                }

                String originalStatus = order.getPaidTime() != null
                        && order.getShippedTime() == null ? "待发货" : "待收货";

                refundMapper.updateStatus(refund.getId(), "已拒绝", rejectReason.trim());

                order.setRefundStatus("已拒绝");
                order.setStatus(originalStatus);
                orderMapper.update(order);
                return 1;
            });
        });

        if (!locked) {
            throw new BizException("操作太频繁，请稍后重试");
        }

        evictDetailCache(orderId);
        Order order = orderMapper.findById(orderId);
        if (order != null) {
            evictListCache(order.getBuyerId());
            if (order.getSellerId() != null) {
                evictListCache(order.getSellerId());
            }
        }
        productService.evictAllCaches();
    }

    @Override
    public Refund getLatestRefund(Long orderId) {
        return refundMapper.findLatestByOrderId(orderId);
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

    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = (int) (Math.random() * 10000);
        return "ORD" + timestamp + String.format("%04d", random);
    }

    private String generateRefundNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = (int) (Math.random() * 10000);
        return "REF" + timestamp + String.format("%04d", random);
    }

    private void evictDetailCache(Long orderId) {
        try {
            redisTemplate.delete("order:" + orderId);
        } catch (Exception e) {
            log.error("删除订单缓存失败, id={}", orderId, e);
        }
    }

    private void evictProductCache(Long productId, Long serviceId) {
        try {
            if (productId != null) {
                redisTemplate.delete("product:" + productId);
            }
            if (serviceId != null) {
                redisTemplate.delete("product:" + serviceId);
            }
        } catch (Exception e) {
            log.error("删除商品缓存失败", e);
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
