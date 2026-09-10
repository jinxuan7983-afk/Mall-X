package com.mallx.mgr.service;

import com.mallx.common.constant.MallConstant;
import com.mallx.common.mq.OrderCloseEvent;
import com.mallx.common.mq.PayCloseEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 订单事件消息发送：RocketMQ 延迟消息实现订单超时自动关闭（默认30分钟，级别16）。
 */
@Slf4j
@Component
public class OrderMqSender {

    private final RocketMQTemplate rocketMQTemplate;

    @Value("${mallx.order.close-delay-level:16}")
    private int closeDelayLevel;

    public OrderMqSender(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    public void sendOrderCloseDelay(OrderCloseEvent event) {
        try {
            SendResult result = rocketMQTemplate.syncSend(MallConstant.TOPIC_ORDER_CLOSE,
                    MessageBuilder.withPayload(event).build(), 3000, closeDelayLevel);
            log.info("订单延迟关闭消息已发送 orderNum={} delayLevel={} status={}",
                    event.getOrderNum(), closeDelayLevel, result.getSendStatus());
        } catch (Exception e) {
            // MQ不可用时订单仍创建成功，仅失去自动关单能力（降级记录日志）
            log.error("订单延迟关闭消息发送失败 orderNum={}", event.getOrderNum(), e);
        }
    }
}
