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
            if (product != null) {
                order.setPrice(product.getPrice());
                order.setTotalAmount(product.getPrice());
                order.setSellerId(product.getSellerId());
            }
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
