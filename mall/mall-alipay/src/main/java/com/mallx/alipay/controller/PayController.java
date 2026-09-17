package com.mallx.alipay.controller;

import com.mallx.alipay.dto.CallbackReq;
import com.mallx.alipay.dto.PayCreateReq;
import com.mallx.alipay.dto.PayListReq;
import com.mallx.alipay.dto.PayQueryReq;
import com.mallx.alipay.service.PayService;
import com.mallx.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付交易核心接口（接口文档 4.2 / 4.3）。
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;

    /**
     * 收银台页面/沙箱回跳对外可访问的基址（如 http://localhost:10010）。
     * 留空时回退到按当前请求推导；但网关经 lb:// 转发会给下游带上实例内网地址，
     * 容器化部署下即为 172.x.x.x，浏览器（宿主机）无法访问，必须显式配置 PAY_MOCK_BASE_URL。
     */
    @Value("${mallx.pay.mock-base-url:}")
    private String mockBaseUrl;

    /** 4.2.1 统一支付下单接口 */
    @PostMapping("/pay/create")
    public R create(@Valid @RequestBody PayCreateReq req, HttpServletRequest request) {
        String base = (mockBaseUrl == null || mockBaseUrl.isBlank())
                ? request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
                : mockBaseUrl;
        return payService.create(req, base);
    }

    /** 4.2.2 支付结果回调接口（幂等处理） */
    @PostMapping("/pay/callback")
    public R callback(@Valid @RequestBody CallbackReq req) {
        return payService.callback(req.getPayOrderId(), req.getChannelOrderNo(), req.getPayState(), req.getPayTime());
    }

    /** 4.3.1 订单查询接口 */
    @PostMapping("/pay/query")
    public R query(@Valid @RequestBody PayQueryReq req) {
        return payService.query(req.getShopNum(), req.getPayOrderNum());
    }

    /** 4.3.2 商户订单列表分页接口 */
    @PostMapping("/pay/list/mch")
    public R listMch(@Valid @RequestBody PayListReq req) {
        return payService.listMch(req);
    }
}
