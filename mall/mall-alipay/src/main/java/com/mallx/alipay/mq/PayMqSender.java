package com.mallx.alipay.mq;

import com.mallx.common.constant.MallConstant;
import com.mallx.common.mq.PayCloseEvent;
import com.mallx.common.mq.PaySuccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 支付事件消息发送：
 * 1. 支付成功事件 → 业务微服务消费，驱动订单状态流转（异步解耦）；
 * 2. 订单超时关闭延迟消息 → 30分钟未支付自动关单（RocketMQ 延迟级别16 = 30分钟）。
 */
@Slf4j
@Component
public class PayMqSender {

    private final RocketMQTemplate rocketMQTemplate;

    @org.springframework.beans.factory.annotation.Value("${mallx.pay.close-delay-level:16}")
    private int closeDelayLevel;

    public PayMqSender(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    public void sendPaySuccess(PaySuccessEvent event) {
        try {
            SendResult result = rocketMQTemplate.syncSend(MallConstant.TOPIC_PAY_SUCCESS,
                    MessageBuilder.withPayload(event).build());
            log.info("支付成功事件已发送 payOrderId={} status={}", event.getPayOrderId(), result.getSendStatus());
        } catch (Exception e) {
            log.error("支付成功事件发送失败 payOrderId={}", event.getPayOrderId(), e);
        }
    }

    public void sendPayCloseDelay(PayCloseEvent event) {
        try {
            SendResult result = rocketMQTemplate.syncSend(MallConstant.TOPIC_PAY_CLOSE,
                    MessageBuilder.withPayload(event).build(), 3000, closeDelayLevel);
            log.info("支付订单延迟关闭消息已发送 payOrderId={} delayLevel={}", event.getPayOrderId(), closeDelayLevel);
        } catch (Exception e) {
            log.error("支付订单延迟关闭消息发送失败 payOrderId={}", event.getPayOrderId(), e);
        }
    }
}
