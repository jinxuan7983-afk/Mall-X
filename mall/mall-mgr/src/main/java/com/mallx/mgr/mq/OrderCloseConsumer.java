package com.mallx.mgr.mq;

import com.mallx.common.constant.MallConstant;
import com.mallx.common.mq.OrderCloseEvent;
import com.mallx.mgr.service.OrderService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 订单超时关闭消费者：延迟消息到期后关单、回补库存、联动关闭支付单。
 */
@Component
@RocketMQMessageListener(topic = MallConstant.TOPIC_ORDER_CLOSE,
        consumerGroup = "mall-mgr-order-close-group")
public class OrderCloseConsumer implements RocketMQListener<OrderCloseEvent> {

    private final OrderService orderService;

    public OrderCloseConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void onMessage(OrderCloseEvent event) {
        orderService.closeTimeoutOrder(event);
    }
}
