package com.mallx.alipay.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mallx.alipay.entity.PayOrder;
import com.mallx.alipay.mapper.PayOrderMapper;
import com.mallx.alipay.service.PayService;
import com.mallx.common.constant.MallConstant;
import com.mallx.common.exception.BizException;
import com.mallx.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 内部接口：仅供业务微服务(mall-mgr)通过 Feign 调用，通过内部令牌鉴权，不对外签名。
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final PayService payService;
    private final PayOrderMapper payOrderMapper;

    @Value("${mallx.internal.token:mallx-internal-token}")
    private String internalToken;

    /** 按商户订单号关闭待支付支付单（业务订单超时关闭联动） */
    @PostMapping("/pay/close")
    public R closeByMchOrderNo(@RequestHeader(value = MallConstant.HEADER_INTERNAL_TOKEN, required = false) String token,
                               @RequestBody Map<String, String> body) {
        checkToken(token);
        return payService.closeByMchOrderNo(body.get("mchOrderNo"), body.get("shopNum"));
    }

    /** 渠道退款执行：返回渠道退款流水号 */
    @PostMapping("/refund/execute")
    public R executeRefund(@RequestHeader(value = MallConstant.HEADER_INTERNAL_TOKEN, required = false) String token,
                           @RequestBody Map<String, Object> body) {
        checkToken(token);
        String payOrderId = String.valueOf(body.get("payOrderId"));
        BigDecimal refundMoney = new BigDecimal(String.valueOf(body.get("refundMoney")));
        String refundReason = String.valueOf(body.getOrDefault("refundReason", "售后退款"));
        String channelRefundNo = payService.executeRefund(payOrderId, refundMoney, refundReason);
        return R.ok("渠道退款执行成功").set("channelRefundNo", channelRefundNo);
    }

    /** 支付订单详情查询 */
    @PostMapping("/pay/query")
    public R query(@RequestHeader(value = MallConstant.HEADER_INTERNAL_TOKEN, required = false) String token,
                   @RequestBody Map<String, String> body) {
        checkToken(token);
        PayOrder po = payOrderMapper.selectOne(Wrappers.<PayOrder>lambdaQuery()
                .eq(PayOrder::getPayOrderId, body.get("payOrderId")));
        if (po == null) {
            throw new BizException("支付订单不存在");
        }
        return R.ok("查询成功").set("payOrder", po);
    }

    private void checkToken(String token) {
        if (token == null || !token.equals(internalToken)) {
            throw new BizException("内部接口鉴权失败");
        }
    }
}
