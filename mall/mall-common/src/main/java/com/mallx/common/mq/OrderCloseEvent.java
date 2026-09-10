package com.mallx.common.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业务订单超时关闭事件（RocketMQ 延迟消息：下单后30分钟未支付自动关单并回补库存）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCloseEvent {

    private String orderNum;

    private String shopNum;
}
