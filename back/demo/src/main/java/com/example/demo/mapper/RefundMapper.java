package com.example.demo.mapper;

import com.example.demo.entity.Refund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RefundMapper {

    /**
     * 插入退款申请
     */
    int insert(Refund refund);

    /**
     * 根据ID查询
     */
    Refund findById(@Param("id") Long id);

    /**
     * 根据退款单号查询
     */
    Refund findByRefundNo(@Param("refundNo") String refundNo);

    /**
     * 根据订单ID查询最新的退款记录
     */
    Refund findLatestByOrderId(@Param("orderId") Long orderId);

    /**
     * 更新退款状态(同意/拒绝)
     */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("rejectReason") String rejectReason);
}