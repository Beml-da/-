package com.example.demo.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Refund {
    private Long id;
    private String refundNo;
    private Long orderId;
    private String orderNo;
    private Long applicantId;
    private Long sellerId;
    private BigDecimal amount;
    private String reason;
    private String status;
    private String rejectReason;
    private LocalDateTime processedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Refund() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRefundNo() { return refundNo; }
    public void setRefundNo(String refundNo) { this.refundNo = refundNo; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }

    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public LocalDateTime getProcessedTime() { return processedTime; }
    public void setProcessedTime(LocalDateTime processedTime) { this.processedTime = processedTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}