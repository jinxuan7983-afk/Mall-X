package com.mallx.mgr.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mallx.common.constant.MallConstant;
import com.mallx.common.mq.PaySuccessEvent;
import com.mallx.mgr.entity.Goods;
import com.mallx.mgr.entity.OrderItem;
import com.mallx.mgr.mapper.GoodsMapper;
import com.mallx.mgr.mapper.OrderItemMapper;
import com.mallx.mgr.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付成功消费者：支付微服务回调成功后发布事件，业务侧异步驱动订单状态流转（消息解耦），
 * 并累加商品销量。状态机条件更新保证消息重复消费的幂等性。
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = MallConstant.TOPIC_PAY_SUCCESS,
        consumerGroup = "mall-mgr-pay-success-group")
public class PaySuccessConsumer implements RocketMQListener<PaySuccessEvent> {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final GoodsMapper goodsMapper;

    public PaySuccessConsumer(OrderMapper orderMapper, OrderItemMapper orderItemMapper, GoodsMapper goodsMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.goodsMapper = goodsMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(PaySuccessEvent event) {
        LocalDateTime payTime = event.getPayTime() == null ? LocalDateTime.now()
                : LocalDateTime.parse(event.getPayTime(),
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String payTimeStr = payTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        int updated = orderMapper.markPaid(event.getMchOrderNo(), event.getPayOrderId(), payTimeStr);
        if (updated == 0) {
            log.info("支付成功消息重复消费/订单非待支付，幂等跳过 mchOrderNo={}", event.getMchOrderNo());
            return;
        }
        List<OrderItem> items = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderNum, event.getMchOrderNo()));
        items.forEach(oi -> goodsMapper.update(null, Wrappers.<Goods>lambdaUpdate()
                .setSql("sales = sales + " + oi.getGoodsCount())
                .eq(Goods::getGoodsNum, oi.getGoodsNum())));
        log.info("订单支付成功流转完成 orderNum={} payOrderId={}", event.getMchOrderNo(), event.getPayOrderId());
    }
}
