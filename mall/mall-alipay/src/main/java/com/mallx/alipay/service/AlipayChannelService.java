package com.mallx.alipay.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.mallx.alipay.entity.ChannelConfig;
import com.mallx.alipay.entity.PayOrder;
import com.mallx.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

/**
 * 支付宝沙箱渠道对接：页面支付下单 + 渠道退款 + 异步通知验签。
 * 渠道配置(私钥/公钥/网关)取自 t_channel_config，可在管理端动态调整。
 */
@Slf4j
@Service
public class AlipayChannelService {

    @Value("${mallx.pay.alipay-notify-url:http://localhost:10010/pay/callback/alipay}")
    private String alipayNotifyUrl;

    @Value("${mallx.pay.alipay-return-url:http://localhost:10010/pay/mock/cashier}")
    private String alipayReturnUrl;

    private static final String SIGN_TYPE = "RSA2";
    private static final String CHARSET = "UTF-8";
    private static final String FORMAT = "json";

    /** 创建支付宝页面支付，返回跳转 URL（表单提交地址） */
    public String tradePagePay(PayOrder payOrder, ChannelConfig config) {
        try {
            AlipayClient client = buildClient(config);
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setNotifyUrl(alipayNotifyUrl);
            request.setReturnUrl(alipayReturnUrl);
            request.setBizContent("""
                    {"out_trade_no":"%s","total_amount":"%s","subject":"%s","product_code":"FAST_INSTANT_TRADE_PAY"}
                    """.formatted(payOrder.getPayOrderId(), payOrder.getMoney(), escape(payOrder.getSubject())));
            AlipayTradePagePayResponse response = client.pageExecute(request);
            if (!response.isSuccess()) {
                throw new BizException("支付宝下单失败: " + response.getSubMsg());
            }
            return response.getBody();
        } catch (AlipayApiException e) {
            log.error("支付宝下单异常", e);
            throw new BizException("支付宝渠道调用异常");
        }
    }

    /** 渠道退款，成功返回渠道退款流水号 */
    public String tradeRefund(PayOrder payOrder, BigDecimal refundMoney, String refundReason, ChannelConfig config) {
        try {
            AlipayClient client = buildClient(config);
            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            request.setBizContent("""
                    {"out_trade_no":"%s","refund_amount":"%s","refund_reason":"%s"}
                    """.formatted(payOrder.getPayOrderId(), refundMoney, escape(refundReason)));
            AlipayTradeRefundResponse response = client.execute(request);
            if (!response.isSuccess()) {
                throw new BizException("支付宝退款失败: " + response.getSubMsg());
            }
            return response.getTradeNo();
        } catch (AlipayApiException e) {
            log.error("支付宝退款异常", e);
            throw new BizException("支付宝渠道退款异常");
        }
    }

    /** 支付宝异步通知 RSA2 验签 */
    public boolean verifyNotify(Map<String, String> params, ChannelConfig config) {
        try {
            return AlipaySignature.rsaCheckV1(new TreeMap<>(params), config.getAlipayPublicKey(), CHARSET, SIGN_TYPE);
        } catch (AlipayApiException e) {
            log.error("支付宝验签异常", e);
            return false;
        }
    }

    private AlipayClient buildClient(ChannelConfig config) {
        return new DefaultAlipayClient(config.getGatewayUrl(), config.getAppId(), config.getPrivateKey(),
                FORMAT, CHARSET, config.getAlipayPublicKey(), SIGN_TYPE);
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\"", "'");
    }
}
