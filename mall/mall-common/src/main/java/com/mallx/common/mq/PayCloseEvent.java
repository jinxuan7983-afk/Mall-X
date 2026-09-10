package com.mallx.common.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付订单超时关闭事件（RocketMQ 延迟消息：订单创建后30分钟未支付自动关闭）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayCloseEvent {

    private String payOrderId;

    private String shopNum;
}
