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

    /** 关联业务订单号（用户端按订单发起时记录） */
    private String orderNum;

    private String shopNum;

    /** 申请用户编号（用户端发起时记录，商户代发时为空） */
    private String userNum;

    private BigDecimal refundMoney;

    private String refundReason;

    /** 退款凭证图片地址（JSON 数组字符串） */
    private String evidence;

    /** 0-待审核 1-处理中(审核通过,渠道退款执行中) 2-已完成 3-已拒绝 */
    private Integer refundState;

    private String channelRefundNo;

    /** 审核/处理备注 */
    private String auditRemark;

    /** 审核时间 */
    private LocalDateTime auditTime;

    private LocalDateTime refundTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
