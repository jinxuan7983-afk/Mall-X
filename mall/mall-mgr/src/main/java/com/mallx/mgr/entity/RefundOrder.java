package com.mallx.mgr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款订单。
 */
@Data
@TableName("t_refund_order")
public class RefundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String refundOrderId;

    private String payOrderId;

    private String shopNum;

    private BigDecimal refundMoney;

    private String refundReason;

    /** 0-审核中 1-退款成功 2-退款失败 */
    private Integer refundState;

    private String channelRefundNo;

    private String auditRemark;

    private LocalDateTime refundTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
