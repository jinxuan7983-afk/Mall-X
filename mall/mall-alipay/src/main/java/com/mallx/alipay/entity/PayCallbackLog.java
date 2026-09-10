package com.mallx.alipay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付回调流水：以 (pay_order_id, channel_order_no) 唯一索引实现回调幂等的第一重防护。
 */
@Data
@TableName("t_pay_callback")
public class PayCallbackLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String payOrderId;

    private String channelOrderNo;

    /** 0-成功 1-失败 */
    private Integer payState;

    private LocalDateTime payTime;

    private LocalDateTime createdAt;
}
