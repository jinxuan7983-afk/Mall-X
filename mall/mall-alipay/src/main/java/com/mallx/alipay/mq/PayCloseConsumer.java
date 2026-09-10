package com.mallx.alipay.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mallx.alipay.entity.PayOrder;
import com.mallx.alipay.mapper.PayOrderMapper;
import com.mallx.common.constant.MallConstant;
import com.mallx.common.mq.PayCloseEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 支付订单超时关闭消费者：RocketMQ 延迟消息到期后检查订单，待支付订单自动关闭。
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = MallConstant.TOPIC_PAY_CLOSE,
        consumerGroup = "mall-alipay-pay-close-group")
public class PayCloseConsumer implements RocketMQListener<PayCloseEvent> {

    private final PayOrderMapper payOrderMapper;

    public PayCloseConsumer(PayOrderMapper payOrderMapper) {
        this.payOrderMapper = payOrderMapper;
    }

    @Override
    public void onMessage(PayCloseEvent event) {
        int updated = payOrderMapper.update(null, Wrappers.<PayOrder>lambdaUpdate()
                .set(PayOrder::getOrderState, MallConstant.PAY_STATE_CLOSED)
                .eq(PayOrder::getPayOrderId, event.getPayOrderId())
                .eq(PayOrder::getOrderState, MallConstant.PAY_STATE_WAIT));
        log.info("支付订单超时检查 payOrderId={} closed={}", event.getPayOrderId(), updated > 0);
    }
}
