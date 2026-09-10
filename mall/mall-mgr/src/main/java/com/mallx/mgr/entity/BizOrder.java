package com.mallx.mgr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 业务订单（用户购物订单）。
 */
@Data
@TableName("t_order")
public class BizOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNum;

    private String userNum;

    private String shopNum;

    private BigDecimal totalMoney;

    /** 0-待支付 1-已支付待发货 2-已发货 3-已完成 4-已关闭 */
    private Integer orderState;

    private String payOrderId;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String logisticsCompany;

    private String logisticsNo;

    private LocalDateTime payTime;

    private LocalDateTime deliverTime;

    private LocalDateTime receiveTime;

    private LocalDateTime closeTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
