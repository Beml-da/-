package com.example.demo.mapper;

import com.example.demo.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface OrderMapper {

    /**
     * 根据ID查询
     */
    Order findById(@Param("id") Long id);

    /**
     * 根据订单号查询
     */
    Order findByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 根据买家ID查询订单列表
     */
    List<Order> findByBuyerId(@Param("buyerId") Long buyerId);

    /**
     * 根据卖家ID查询订单列表
     */
    List<Order> findBySellerId(@Param("sellerId") Long sellerId);

    /**
     * 查询用户的订单（买家的或卖家的）
     */
    List<Order> findByUserId(@Param("userId") Long userId);

    /**
     * 条件分页查询订单列表
     */
    List<Order> findList(@Param("userId") Long userId,
                         @Param("status") String status,
                         @Param("type") String type,
                         @Param("role") String role,
                         @Param("offset") Integer offset,
                         @Param("limit") Integer limit);

    /**
     * 统计订单数量
     */
    Long count(@Param("userId") Long userId,
               @Param("status") String status,
               @Param("type") String type,
               @Param("role") String role);

    /**
     * 新增订单
     */
    int insert(Order order);

    /**
     * 更新订单
     */
    int update(Order order);

    /**
     * 更新订单状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 删除订单
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据商品ID查询订单
     */
    List<Order> findByProductId(@Param("productId") Long productId);
}
