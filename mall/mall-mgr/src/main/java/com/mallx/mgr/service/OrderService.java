package com.mallx.mgr.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mallx.common.constant.MallConstant;
import com.mallx.common.exception.BizException;
import com.mallx.common.mq.OrderCloseEvent;
import com.mallx.common.result.R;
import com.mallx.common.util.OrderNoUtil;
import com.mallx.mgr.dto.DeliverReq;
import com.mallx.mgr.dto.OrderCreateReq;
import com.mallx.mgr.dto.OrderListReq;
import com.mallx.mgr.entity.BizOrder;
import com.mallx.mgr.entity.Goods;
import com.mallx.mgr.entity.OrderItem;
import com.mallx.mgr.feign.PayClient;
import com.mallx.mgr.mapper.GoodsMapper;
import com.mallx.mgr.mapper.OrderItemMapper;
import com.mallx.mgr.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单服务：下单（Lua 预扣库存）、订单查询、商户发货、用户确认收货。
 * 下单后发送 RocketMQ 延迟消息，30分钟未支付自动关单并回补库存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final GoodsMapper goodsMapper;
    private final StockService stockService;
    private final OrderMqSender orderMqSender;
    private final PayClient payClient;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @org.springframework.beans.factory.annotation.Value("${mallx.internal.token:mallx-internal-token}")
    private String internalToken;

    @Transactional(rollbackFor = Exception.class)
    public R create(OrderCreateReq req) {
        // 1. 校验商品（单订单仅支持同一商户）
        List<OrderCreateReq.Item> items = req.getItems();
        List<Goods> goodsList = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (OrderCreateReq.Item item : items) {
            if (item.getCount() == null || item.getCount() <= 0) {
                throw new BizException("购买数量必须大于0");
            }
            Goods goods = goodsMapper.selectOne(Wrappers.<Goods>lambdaQuery()
                    .eq(Goods::getGoodsNum, item.getGoodsNum()));
            if (goods == null || goods.getGoodsState() != 1) {
                throw new BizException("商品[" + item.getGoodsNum() + "]不存在或已下架");
            }
            if (!goods.getShopNum().equals(req.getShopNum())) {
                throw new BizException("一个订单仅支持同一商户的商品，请分开提交");
            }
            goodsList.add(goods);
            total = total.add(goods.getPrice().multiply(BigDecimal.valueOf(item.getCount())));
        }

        // 2. 创建订单与明细
        String orderNum = OrderNoUtil.orderNum();
        BizOrder order = new BizOrder();
        order.setOrderNum(orderNum);
        order.setUserNum(req.getUserNum());
        order.setShopNum(req.getShopNum());
        order.setTotalMoney(total);
        order.setOrderState(MallConstant.ORDER_WAIT_PAY);
        order.setReceiverName(req.getReceiverName());
        order.setReceiverPhone(req.getReceiverPhone());
        order.setReceiverAddress(req.getReceiverAddress());
        orderMapper.insert(order);
        for (int i = 0; i < items.size(); i++) {
            Goods goods = goodsList.get(i);
            OrderItem oi = new OrderItem();
            oi.setOrderNum(orderNum);
            oi.setGoodsNum(goods.getGoodsNum());
            oi.setGoodsName(goods.getGoodsName());
            oi.setGoodsPrice(goods.getPrice());
            oi.setGoodsCount(items.get(i).getCount());
            oi.setTotalMoney(goods.getPrice().multiply(BigDecimal.valueOf(items.get(i).getCount())));
            orderItemMapper.insert(oi);
        }

        // 3. Redis Lua 原子预扣库存 + 数据库条件扣减（防超卖），失败回滚并回补 Redis
        List<String> deducted = new ArrayList<>();
        try {
            for (int i = 0; i < items.size(); i++) {
                Goods goods = goodsList.get(i);
                int count = items.get(i).getCount();
                stockService.deduct(goods.getGoodsNum(), count, goods.getStock());
                deducted.add(goods.getGoodsNum() + ":" + count);
                if (goodsMapper.deductStock(goods.getGoodsNum(), count) == 0) {
                    throw new BizException("商品[" + goods.getGoodsName() + "]库存不足");
                }
            }
        } catch (Exception e) {
            deducted.forEach(s -> {
                String[] parts = s.split(":");
                stockService.restore(parts[0], Integer.parseInt(parts[1]));
            });
            throw e instanceof BizException be ? be : new BizException("下单失败：" + e.getMessage());
        }

        // 4. RocketMQ 延迟消息：超时未支付自动关单 + 回补库存
        orderMqSender.sendOrderCloseDelay(new OrderCloseEvent(orderNum, req.getShopNum()));

        // 5. 失效受影响商品的详情缓存，保证库存展示与库一致
        goodsList.forEach(g -> redisTemplate.delete(MallConstant.KEY_GOODS_CACHE + g.getGoodsNum()));

        return R.ok("下单成功，请尽快支付").set("orderNum", orderNum).set("totalMoney", total);
    }

    /** 订单列表（userNum 查用户订单 / shopNum 查商户订单） */
    public R list(OrderListReq req) {
        if ((req.getUserNum() == null || req.getUserNum().isBlank())
                && (req.getShopNum() == null || req.getShopNum().isBlank())) {
            throw new BizException("userNum与shopNum至少传一个");
        }
        Page<BizOrder> page = orderMapper.selectPage(new Page<>(req.getPageNum(), req.getPageSize()),
                Wrappers.<BizOrder>lambdaQuery()
                        .eq(StringUtils.hasText(req.getUserNum()), BizOrder::getUserNum, req.getUserNum())
                        .eq(StringUtils.hasText(req.getShopNum()), BizOrder::getShopNum, req.getShopNum())
                        .eq(req.getOrderState() != null, BizOrder::getOrderState, req.getOrderState())
                        .orderByDesc(BizOrder::getId));
        R r = R.ok("查询成功");
        r.set("total", page.getTotal());
        r.set("pages", page.getPages());
        r.set("current", page.getCurrent());
        r.set("size", page.getSize());
        r.set("records", page.getRecords().stream().map(this::toMap).toList());
        return r;
    }

    /** 订单详情（含明细） */
    public R detail(String orderNum) {
        BizOrder order = orderMapper.selectOne(Wrappers.<BizOrder>lambdaQuery()
                .eq(BizOrder::getOrderNum, orderNum));
        if (order == null) {
            throw new BizException("订单不存在");
        }
        Map<String, Object> m = toMap(order);
        m.put("items", orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderNum, orderNum)));
        return R.ok("查询成功").set("order", m);
    }

    /** 商户发货：已支付 → 已发货 */
    public R deliver(DeliverReq req) {
        int updated = orderMapper.markDelivered(req.getOrderNum(), req.getLogisticsCompany(), req.getLogisticsNo());
        if (updated == 0) {
            throw new BizException("订单当前状态不可发货");
        }
        return R.ok("发货成功");
    }

    /** 用户确认收货：已发货 → 已完成 */
    public R confirm(String orderNum) {
        int updated = orderMapper.markReceived(orderNum);
        if (updated == 0) {
            throw new BizException("订单当前状态不可确认收货");
        }
        return R.ok("确认收货成功");
    }

    /** 订单超时关闭（RocketMQ 延迟消息消费入口）：关单 + 回补库存 + 联动关闭支付单 */
    public void closeTimeoutOrder(OrderCloseEvent event) {
        BizOrder order = orderMapper.selectOne(Wrappers.<BizOrder>lambdaQuery()
                .eq(BizOrder::getOrderNum, event.getOrderNum()));
        if (order == null || order.getOrderState() != MallConstant.ORDER_WAIT_PAY) {
            return;
        }
        if (orderMapper.markClosed(event.getOrderNum()) > 0) {
            List<OrderItem> items = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                    .eq(OrderItem::getOrderNum, event.getOrderNum()));
            items.forEach(oi -> {
                goodsMapper.restoreStock(oi.getGoodsNum(), oi.getGoodsCount());
                stockService.restore(oi.getGoodsNum(), oi.getGoodsCount());
            });
            log.info("订单超时自动关闭并回补库存 orderNum={}", event.getOrderNum());
        }
        try {
            payClient.closePayOrder(internalToken,
                    Map.of("mchOrderNo", event.getOrderNum(), "shopNum", event.getShopNum()));
        } catch (Exception e) {
            log.warn("联动关闭支付单失败 orderNum={} err={}", event.getOrderNum(), e.getMessage());
        }
    }

    private Map<String, Object> toMap(BizOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderNum", o.getOrderNum());
        m.put("userNum", o.getUserNum());
        m.put("shopNum", o.getShopNum());
        m.put("totalMoney", o.getTotalMoney());
        m.put("orderState", o.getOrderState());
        m.put("payOrderId", o.getPayOrderId());
        m.put("receiverName", o.getReceiverName());
        m.put("receiverPhone", o.getReceiverPhone());
        m.put("receiverAddress", o.getReceiverAddress());
        m.put("logisticsCompany", o.getLogisticsCompany());
        m.put("logisticsNo", o.getLogisticsNo());
        m.put("createdAt", o.getCreatedAt() == null ? null : TIME_FMT.format(o.getCreatedAt()));
        m.put("payTime", o.getPayTime() == null ? null : TIME_FMT.format(o.getPayTime()));
        m.put("deliverTime", o.getDeliverTime() == null ? null : TIME_FMT.format(o.getDeliverTime()));
        m.put("receiveTime", o.getReceiveTime() == null ? null : TIME_FMT.format(o.getReceiveTime()));
        return m;
    }
}
