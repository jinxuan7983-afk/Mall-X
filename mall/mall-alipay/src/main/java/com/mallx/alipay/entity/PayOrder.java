package com.mallx.alipay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付订单。
 */
@Data
@TableName("t_pay_order")
public class PayOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 支付系统唯一订单号 */
    private String payOrderId;

    /** 商户侧订单号 */
    private String mchOrderNo;

    private String shopNum;

    private String shopName;

    private String appId;

    /** 支付方式 WECHAT/ALIPAY/UNIONPAY */
    private String wayCode;

    /** 交易支付金额(元) */
    private BigDecimal money;

    /** 手续费(元) */
    private BigDecimal mchFee;

    /** 商品交易标题 */
    private String subject;

    /** 用户唯一编号 */
    private String userNum;

    /** 0-支付成功 1-支付失败 2-已退款 3-订单关闭 4-待支付 */
    private Integer orderState;

    /** 第三方渠道订单号 */
    private String channelOrderNo;

    /** 支付完成时间 */
    private LocalDateTime payTime;

    /** 订单过期时间 */
    private LocalDateTime expireTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
