package com.example.demo.service;

import com.example.demo.entity.Order;
import com.example.demo.entity.Refund;
import java.util.List;

public interface OrderService {

    /**
     * 创建订单
     */
    Order create(Long buyerId, Order order);

    /**
     * 获取订单详情
     */
    Order getById(Long id);

    /**
     * 获取订单详情（按订单号）
     */
    Order getByOrderNo(String orderNo);

    /**
     * 获取用户的订单列表
     */
    List<Order> list(Long userId, String status, String type, String role, Integer page, Integer pageSize);

    /**
     * 统计订单数量
     */
    Long count(Long userId, String status, String type, String role);

    /**
     * 更新订单状态
     */
    void updateStatus(Long orderId, String status);

    /**
     * 取消订单
     */
    void cancel(Long orderId, Long userId);

    /**
     * 确认发货（卖家）
     */
    void ship(Long orderId, Long userId);

    /**
     * 确认收货（买家）
     */
    void confirmReceive(Long orderId, Long userId);

    /**
     * 支付订单
     */
    void pay(Long orderId, Long userId);

    /**
     * 删除订单
     */
    void delete(Long orderId, Long userId);

    /**
     * 根据商品ID查询订单
     */
    List<Order> getByProductId(Long productId);

    /**
     * 申请退款（买家）
     * @param orderId 订单ID
     * @param userId 申请人（必须是买家）
     * @param reason 退款原因（至少5个字）
     * @return 退款记录
     */
    Refund applyRefund(Long orderId, Long userId, String reason);

    /**
     * 同意退款（卖家）
     */
    void approveRefund(Long orderId, Long userId);

    /**
     * 拒绝退款（卖家）
     */
    void rejectRefund(Long orderId, Long userId, String rejectReason);

    /**
     * 获取订单最新的退款记录
     */
    Refund getLatestRefund(Long orderId);
}
