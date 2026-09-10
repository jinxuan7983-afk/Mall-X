package com.mallx.alipay.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mallx.alipay.entity.PayOrder;
import com.mallx.alipay.mapper.PayOrderMapper;
import com.mallx.alipay.service.PayCallbackService;
import com.mallx.common.constant.MallConstant;
import com.mallx.common.util.OrderNoUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 内置模拟收银台：渠道未配置沙箱参数时用于演示完整支付闭环。
 * 生产环境请设置 mallx.pay.mock-enabled=false 关闭。
 */
@RestController
@RequestMapping("/pay/mock")
@RequiredArgsConstructor
public class MockPayController {

    private final PayOrderMapper payOrderMapper;
    private final PayCallbackService payCallbackService;

    @Value("${mallx.pay.mock-enabled:true}")
    private boolean mockEnabled;

    @GetMapping("/cashier")
    public void cashier(@RequestParam String payOrderId, HttpServletResponse response) throws IOException {
        if (!mockEnabled) {
            response.getWriter().write("mock disabled");
            return;
        }
        PayOrder po = payOrderMapper.selectOne(Wrappers.<PayOrder>lambdaQuery()
                .eq(PayOrder::getPayOrderId, payOrderId));
        if (po == null) {
            response.getWriter().write("order not found");
            return;
        }
        response.setContentType("text/html;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String html = """
                <!DOCTYPE html><html><head><meta charset="utf-8"><title>模拟收银台</title></head>
                <body style="text-align:center;font-family:sans-serif;margin-top:80px">
                  <h2>模拟收银台（%s）</h2>
                  <p>支付订单号：%s</p>
                  <p>支付金额：<b style="color:#e62021;font-size:26px">¥%s</b></p>
                  <p>支付方式：%s</p>
                  <a href="/pay/mock/confirm?payOrderId=%s"
                     style="display:inline-block;padding:12px 48px;background:#e62021;color:#fff;border-radius:24px;text-decoration:none;font-size:18px">
                     确认支付
                  </a>
                  <p style="color:#999">该页面为 Mall-X 内置模拟渠道，演示支付闭环用</p>
                </body></html>
                """.formatted(po.getSubject(), po.getPayOrderId(), po.getMoney(), po.getWayCode(), po.getPayOrderId());
        response.getWriter().write(html);
    }

    /** 模拟渠道扣款成功后向平台发起回调（与真实回调走同一条幂等处理链路） */
    @GetMapping("/confirm")
    public void confirm(@RequestParam String payOrderId, HttpServletResponse response) throws IOException {
        if (!mockEnabled) {
            response.getWriter().write("mock disabled");
            return;
        }
        PayOrder po = payOrderMapper.selectOne(Wrappers.<PayOrder>lambdaQuery()
                .eq(PayOrder::getPayOrderId, payOrderId));
        response.setContentType("text/html;charset=UTF-8");
        if (po == null) {
            response.getWriter().write("order not found");
            return;
        }
        if (po.getOrderState() != MallConstant.PAY_STATE_WAIT) {
            response.getWriter().write("<h2>该订单已处理，请勿重复支付</h2>");
            return;
        }
        payCallbackService.process(po.getPayOrderId(), OrderNoUtil.channelOrderNo(),
                MallConstant.CALLBACK_SUCCESS, LocalDateTime.now());
        response.getWriter().write("<h2 style='color:green'>支付成功</h2><p>订单号：" + po.getPayOrderId() + "</p>");
    }
}
