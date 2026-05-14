package com.example.demo.service.impl;

import com.example.demo.entity.Order;
import com.example.demo.entity.Product;
import com.example.demo.entity.User;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional
    public Order create(Long buyerId, Order order) {
        // 生成订单号
        String orderNo = generateOrderNo();
        order.setOrderNo(orderNo);
        order.setBuyerId(buyerId);
        order.setStatus("待付款");
        order.setQuantity(1);
        order.setTotalAmount(order.getPrice());

        // 如果是商品订单，设置商品标题等信息
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
            // 标记商品为已预订（仍在售，但有活跃订单，不会出现在商品列表）
            productMapper.updateStatus(product.getId(), "已预订");
        }

        // 如果是服务订单，检查不能预约自己的服务
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
            // 标记服务为已预订
            productMapper.updateStatus(service.getId(), "已预订");
        }

        orderMapper.insert(order);
        return order;
    }

    @Override
    public Order getById(Long id) {
        Order order = orderMapper.findById(id);
        if (order != null) {
            enrichOrder(order);
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
        Integer offset = (page - 1) * pageSize;
        List<Order> orders = orderMapper.findList(userId, status, type, role, offset, pageSize);
        // 补充关联信息
        for (Order order : orders) {
            enrichOrder(order);
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
    }

    @Override
    @Transactional
    public void cancel(Long orderId, Long userId) {
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        // 只有买家或卖家可以取消
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new RuntimeException("无权取消此订单");
        }
        // 只有待付款、待发货状态可以取消
        if (!"待付款".equals(order.getStatus()) && !"待发货".equals(order.getStatus())) {
            throw new RuntimeException("当前状态不允许取消");
        }
        // 如果买家已付款（待发货），退款给买家
        if ("待发货".equals(order.getStatus())) {
            User buyer = userMapper.findById(order.getBuyerId());
            if (buyer != null) {
                BigDecimal currentBalance = buyer.getBalance() != null ? buyer.getBalance() : BigDecimal.ZERO;
                BigDecimal newBalance = currentBalance.add(order.getTotalAmount());
                userMapper.updateBalance(order.getBuyerId(), newBalance);
            }
        }
        // 取消后商品恢复在售
        if ("商品".equals(order.getType()) && order.getProductId() != null) {
            productMapper.updateStatus(order.getProductId(), "在售");
        }
        if ("服务".equals(order.getType()) && order.getServiceId() != null) {
            productMapper.updateStatus(order.getServiceId(), "可用");
        }
        orderMapper.updateStatus(orderId, "已取消");
    }

    @Override
    @Transactional
    public void ship(Long orderId, Long userId) {
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        // 只有卖家可以发货
        if (!order.getSellerId().equals(userId)) {
            throw new RuntimeException("只有卖家可以发货");
        }
        if (!"待发货".equals(order.getStatus())) {
            throw new RuntimeException("当前状态不允许发货");
        }
        orderMapper.updateStatus(orderId, "待收货");
    }

    @Override
    @Transactional
    public void confirmReceive(Long orderId, Long userId) {
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        // 只有买家可以确认收货
        if (!order.getBuyerId().equals(userId)) {
            throw new RuntimeException("只有买家可以确认收货");
        }
        if (!"待收货".equals(order.getStatus())) {
            throw new RuntimeException("当前状态不允许确认收货");
        }
        // 将钱转给卖家
        User seller = userMapper.findById(order.getSellerId());
        if (seller != null) {
            BigDecimal currentSellerBalance = seller.getBalance() != null ? seller.getBalance() : BigDecimal.ZERO;
            BigDecimal newSellerBalance = currentSellerBalance.add(order.getTotalAmount());
            userMapper.updateBalance(order.getSellerId(), newSellerBalance);
        }
        // 商品标记为已售出
        if ("商品".equals(order.getType()) && order.getProductId() != null) {
            productMapper.updateStatus(order.getProductId(), "已售出");
        }
        if ("服务".equals(order.getType()) && order.getServiceId() != null) {
            productMapper.updateStatus(order.getServiceId(), "已完成");
        }
        orderMapper.updateStatus(orderId, "已完成");
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
        // 扣减买家余额
        User buyer = userMapper.findById(userId);
        if (buyer == null) {
            throw new RuntimeException("买家账户不存在");
        }
        BigDecimal amount = order.getTotalAmount();
        if (buyer.getBalance() == null || buyer.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("余额不足，无法支付");
        }
        BigDecimal newBuyerBalance = buyer.getBalance().subtract(amount);
        userMapper.updateBalance(userId, newBuyerBalance);
        // 订单状态改为待发货（等待卖家发货/确认）
        orderMapper.updateStatus(orderId, "待发货");
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
    }

    @Override
    public List<Order> getByProductId(Long productId) {
        return orderMapper.findByProductId(productId);
    }

    /**
     * 补充订单关联信息
     */
    private void enrichOrder(Order order) {
        if (order == null) return;

        // 补充买家信息
        if (order.getBuyerId() != null) {
            User buyer = userMapper.findById(order.getBuyerId());
            if (buyer != null) {
                order.setBuyer(buyer);
            }
        }

        // 补充卖家信息
        if (order.getSellerId() != null) {
            User seller = userMapper.findById(order.getSellerId());
            if (seller != null) {
                order.setSeller(seller);
            }
        }

        // 补充商品信息
        if ("商品".equals(order.getType()) && order.getProductId() != null) {
            Product product = productMapper.findById(order.getProductId());
            if (product != null) {
                order.setProduct(product);
            }
        }
    }

    /**
     * 生成订单号
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = (int) (Math.random() * 10000);
        return "ORD" + timestamp + String.format("%04d", random);
    }
}
