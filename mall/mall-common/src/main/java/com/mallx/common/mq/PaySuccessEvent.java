package com.mallx.common.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付成功事件消息（支付微服务 → 业务微服务，异步解耦驱动订单状态流转）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaySuccessEvent {

    private String payOrderId;

    /** 商户侧订单号（即业务订单号） */
    private String mchOrderNo;

    private String shopNum;

    private String wayCode;

    private String payMoney;

    private String payTime;
}
