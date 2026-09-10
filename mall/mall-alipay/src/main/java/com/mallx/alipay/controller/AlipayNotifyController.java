package com.mallx.alipay.controller;

import com.mallx.alipay.entity.ChannelConfig;
import com.mallx.alipay.service.AlipayChannelService;
import com.mallx.alipay.service.ChannelService;
import com.mallx.alipay.service.PayCallbackService;
import com.mallx.common.constant.MallConstant;
import com.mallx.common.exception.BizException;
import com.mallx.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 支付宝官方异步通知入口（RSA2 验签）。
 * 验签通过后转换为平台统一回调处理链路（与 /pay/callback 共用三重幂等逻辑）。
 */
@Slf4j
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class AlipayNotifyController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChannelService channelService;
    private final AlipayChannelService alipayChannelService;
    private final PayCallbackService payCallbackService;

    @PostMapping("/callback/alipay")
    public R alipayNotify(HttpServletRequest request) {
        Map<String, String[]> raw = request.getParameterMap();
        Map<String, String> params = new java.util.TreeMap<>();
        raw.forEach((k, v) -> {
            if (v != null && v.length > 0) {
                params.put(k, v[0]);
            }
        });
        String outTradeNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String tradeStatus = params.get("trade_status");
        String gmtPayment = params.get("gmt_payment");
        log.info("收到支付宝异步通知 outTradeNo={} tradeStatus={}", outTradeNo, tradeStatus);

        if (outTradeNo == null || tradeNo == null) {
            throw new BizException("通知参数缺失");
        }
        ChannelConfig config = channelService.get("ALIPAY");
        if (config == null || config.getAlipayPublicKey() == null || config.getAlipayPublicKey().isBlank()) {
            throw new BizException("支付宝渠道公钥未配置");
        }
        if (!alipayChannelService.verifyNotify(params, config)) {
            throw new BizException("支付宝通知验签失败");
        }
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            // 未成功的通知仅记录，返回成功避免渠道重复通知
            return R.ok("回调处理成功");
        }
        LocalDateTime payTime = gmtPayment == null ? LocalDateTime.now() : LocalDateTime.parse(gmtPayment, TIME_FMT);
        payCallbackService.process(outTradeNo, tradeNo, MallConstant.CALLBACK_SUCCESS, payTime);
        return R.ok("回调处理成功");
    }
}
