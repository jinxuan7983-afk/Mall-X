package com.mallx.alipay.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mallx.alipay.dto.PayCreateReq;
import com.mallx.alipay.dto.PayListReq;
import com.mallx.alipay.entity.ChannelConfig;
import com.mallx.alipay.entity.MchRate;
import com.mallx.alipay.entity.PayOrder;
import com.mallx.alipay.mapper.MchRateMapper;
import com.mallx.alipay.mapper.PayOrderMapper;
import com.mallx.alipay.mq.PayMqSender;
import com.mallx.common.constant.MallConstant;
import com.mallx.common.exception.BizException;
import com.mallx.common.mq.PayCloseEvent;
import com.mallx.common.result.R;
import com.mallx.common.util.OrderNoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付核心服务：统一支付下单（支持微信/支付宝/银联）、订单查询、商户订单分页。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PayOrderMapper payOrderMapper;
    private final MchRateMapper mchRateMapper;
    private final ChannelService channelService;
    private final AlipayChannelService alipayChannelService;
    private final PayMqSender payMqSender;
    private final PayCallbackService payCallbackService;

    @org.springframework.beans.factory.annotation.Value("${mallx.pay.expire-minutes:30}")
    private int expireMinutes;

    @org.springframework.beans.factory.annotation.Value("${mallx.pay.mock-enabled:true}")
    private boolean mockEnabled;

    /** 统一支付下单（幂等：同一商户订单号重复下单返回已有支付单） */
    public R create(PayCreateReq req, String requestBaseUrl) {
        ChannelConfig channel = channelService.get(req.getWayCode());
        if (channel == null) {
            throw new BizException("不支持的支付方式: " + req.getWayCode());
        }
        if (channel.getSwitchStatus() == null || channel.getSwitchStatus() != 1) {
            throw new BizException("支付渠道[" + channel.getChannelName() + "]已关闭，请更换支付方式");
        }

        PayOrder exist = payOrderMapper.selectOne(Wrappers.<PayOrder>lambdaQuery()
                .eq(PayOrder::getShopNum, req.getShopNum())
                .eq(PayOrder::getMchOrderNo, req.getOrderNum()));
        if (exist != null) {
            if (MallConstant.PAY_STATE_WAIT == exist.getOrderState()
                    && exist.getExpireTime() != null && exist.getExpireTime().isAfter(LocalDateTime.now())) {
                return buildCreateResult(exist, req.getOrderNum(), payUrlOf(exist, channel, requestBaseUrl));
            }
            throw new BizException("商户订单号[" + req.getOrderNum() + "]已存在且不可重复下单");
        }

        PayOrder payOrder = new PayOrder();
        payOrder.setPayOrderId(OrderNoUtil.payOrderId());
        payOrder.setMchOrderNo(req.getOrderNum());
        payOrder.setShopNum(req.getShopNum());
        payOrder.setShopName(req.getShopName());
        payOrder.setAppId(req.getAppId());
        payOrder.setWayCode(req.getWayCode());
        payOrder.setMoney(req.getPayMoney());
        payOrder.setMchFee(calcFee(req.getPayMoney(), req.getShopNum(), req.getWayCode(), channel));
        payOrder.setSubject(req.getSubject());
        payOrder.setUserNum(req.getUserNum());
        payOrder.setOrderState(MallConstant.PAY_STATE_WAIT);
        payOrder.setExpireTime(LocalDateTime.now().plusMinutes(expireMinutes));
        try {
            payOrderMapper.insert(payOrder);
        } catch (DuplicateKeyException e) {
            // 并发下单兜底：数据库唯一索引(shop_num, mch_order_no)保证只有一笔支付单
            throw new BizException("商户订单号[" + req.getOrderNum() + "]正在下单，请勿重复提交");
        }

        // RocketMQ 延迟消息：超时未支付自动关单
        payMqSender.sendPayCloseDelay(new PayCloseEvent(payOrder.getPayOrderId(), payOrder.getShopNum()));

        return buildCreateResult(payOrder, req.getOrderNum(), payUrlOf(payOrder, channel, requestBaseUrl));
    }

    /** 支付结果回调处理（分布式锁 + 唯一索引 + 状态机 三重幂等） */
    public R callback(String payOrderId, String channelOrderNo, String payState, String payTime) {
        int state;
        try {
            state = Integer.parseInt(payState);
        } catch (NumberFormatException e) {
            throw new BizException("payState不合法");
        }
        if (state != MallConstant.CALLBACK_SUCCESS && state != MallConstant.CALLBACK_FAIL) {
            throw new BizException("payState仅支持0-成功/1-失败");
        }
        LocalDateTime payDateTime;
        try {
            payDateTime = LocalDateTime.parse(payTime, TIME_FMT);
        } catch (Exception e) {
            throw new BizException("payTime格式应为 yyyy-MM-dd HH:mm:ss");
        }
        return payCallbackService.process(payOrderId, channelOrderNo, state, payDateTime);
    }

    /** 订单查询（接口文档 4.3.1） */
    public R query(String shopNum, String payOrderNum) {
        PayOrder po = payOrderMapper.selectOne(Wrappers.<PayOrder>lambdaQuery()
                .eq(PayOrder::getPayOrderId, payOrderNum));
        if (po == null || !po.getShopNum().equals(shopNum)) {
            throw new BizException("支付订单不存在");
        }
        R r = R.ok("查询订单成功");
        r.set("payOrderId", po.getPayOrderId());
        r.set("mchOrderNo", po.getMchOrderNo());
        r.set("appId", po.getAppId());
        r.set("wayCode", po.getWayCode());
        r.set("money", po.getMoney());
        r.set("subject", po.getSubject());
        r.set("createdAt", po.getCreatedAt() == null ? null : TIME_FMT.format(po.getCreatedAt()));
        r.set("payTime", po.getPayTime() == null ? null : TIME_FMT.format(po.getPayTime()));
        r.set("orderState", po.getOrderState());
        return r;
    }

    /** 商户订单列表分页（接口文档 4.3.2） */
    public R listMch(PayListReq req) {
        Page<PayOrder> page = new Page<>(req.getPageNum(), req.getPageSize());
        Page<PayOrder> result = payOrderMapper.selectPage(page, Wrappers.<PayOrder>lambdaQuery()
                .eq(PayOrder::getShopNum, req.getShopNum())
                .eq(req.getOrderState() != null, PayOrder::getOrderState, req.getOrderState())
                .eq(req.getWayCode() != null && !req.getWayCode().isEmpty(), PayOrder::getWayCode, req.getWayCode())
                .orderByDesc(PayOrder::getId));
        R r = R.ok("查询成功");
        r.set("total", result.getTotal());
        r.set("pages", result.getPages());
        r.set("current", result.getCurrent());
        r.set("size", result.getSize());
        r.set("records", result.getRecords().stream().map(po -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("payOrderId", po.getPayOrderId());
            m.put("mchOrderNo", po.getMchOrderNo());
            m.put("wayCode", po.getWayCode());
            m.put("money", po.getMoney());
            m.put("mchFee", po.getMchFee());
            m.put("orderState", po.getOrderState());
            m.put("createdAt", po.getCreatedAt() == null ? null : TIME_FMT.format(po.getCreatedAt()));
            m.put("payTime", po.getPayTime() == null ? null : TIME_FMT.format(po.getPayTime()));
            return m;
        }).toList());
        return r;
    }

    /** 根据商户订单号关闭待支付支付单（业务订单超时关闭时由业务微服务调用） */
    public R closeByMchOrderNo(String mchOrderNo, String shopNum) {
        int updated = payOrderMapper.update(null, Wrappers.<PayOrder>lambdaUpdate()
                .set(PayOrder::getOrderState, MallConstant.PAY_STATE_CLOSED)
                .eq(PayOrder::getMchOrderNo, mchOrderNo)
                .eq(PayOrder::getShopNum, shopNum)
                .eq(PayOrder::getOrderState, MallConstant.PAY_STATE_WAIT));
        log.info("关闭支付订单 mchOrderNo={} shopNum={} updated={}", mchOrderNo, shopNum, updated);
        return R.ok("支付订单关闭成功").set("updated", updated);
    }

    /** 渠道退款执行：支付宝真实通道或模拟通道，返回渠道退款流水号 */
    public String executeRefund(String payOrderId, BigDecimal refundMoney, String refundReason) {
        PayOrder po = payOrderMapper.selectOne(Wrappers.<PayOrder>lambdaQuery()
                .eq(PayOrder::getPayOrderId, payOrderId));
        if (po == null) {
            throw new BizException("原支付订单不存在");
        }
        ChannelConfig channel = channelService.get(po.getWayCode());
        if (channel != null && channelService.useRealAlipay(channel) && "ALIPAY".equals(po.getWayCode())) {
            return alipayChannelService.tradeRefund(po, refundMoney, refundReason, channel);
        }
        // 模拟渠道退款成功
        return OrderNoUtil.channelOrderNo();
    }

    private R buildCreateResult(PayOrder payOrder, String mchOrderNo, String payUrl) {
        R r = R.ok("下单成功");
        r.set("payOrderId", payOrder.getPayOrderId());
        r.set("mchOrderNo", mchOrderNo);
        r.set("payUrl", payUrl);
        r.set("orderState", payOrder.getOrderState());
        return r;
    }

    private String payUrlOf(PayOrder payOrder, ChannelConfig channel, String requestBaseUrl) {
        if (channelService.useRealAlipay(channel) && "ALIPAY".equals(payOrder.getWayCode())) {
            return alipayChannelService.tradePagePay(payOrder, channel);
        }
        if (!mockEnabled) {
            throw new BizException("渠道未配置，且模拟支付通道已关闭");
        }
        return requestBaseUrl + "/pay/mock/cashier?payOrderId=" + payOrder.getPayOrderId();
    }

    private BigDecimal calcFee(BigDecimal money, String shopNum, String wayCode, ChannelConfig channel) {
        MchRate mchRate = mchRateMapper.selectOne(Wrappers.<MchRate>lambdaQuery()
                .eq(MchRate::getShopNum, shopNum)
                .eq(MchRate::getWayCode, wayCode));
        BigDecimal rate = mchRate != null ? mchRate.getMchRate() : channel.getBaseRate();
        return money.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
