package com.mallx.mgr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付订单（业务微服务只读视图：统计/退款校验/平台订单管控使用）。
 */
@Data
@TableName("t_pay_order")
public class PayOrderView {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String payOrderId;

    private String mchOrderNo;

    private String shopNum;

    private String shopName;

    private String appId;

    private String wayCode;

    private BigDecimal money;

    private BigDecimal mchFee;

    private String subject;

    private String userNum;

    /** 0-支付成功 1-支付失败 2-已退款 3-订单关闭 4-待支付 */
    private Integer orderState;

    private String channelOrderNo;

    private LocalDateTime payTime;

    private LocalDateTime expireTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
