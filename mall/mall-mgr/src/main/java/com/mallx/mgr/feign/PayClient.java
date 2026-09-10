package com.mallx.mgr.feign;

import com.mallx.common.constant.MallConstant;
import com.mallx.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * 支付微服务内部调用客户端：支付单关闭、渠道退款执行。
 */
@FeignClient(name = "mall-alipay")
public interface PayClient {

    /** 按商户订单号关闭待支付支付单（业务订单超时关闭联动） */
    @PostMapping("/internal/pay/close")
    R closePayOrder(@RequestHeader(MallConstant.HEADER_INTERNAL_TOKEN) String internalToken,
                    @RequestBody Map<String, String> body);

    /** 渠道退款执行 */
    @PostMapping("/internal/refund/execute")
    R executeRefund(@RequestHeader(MallConstant.HEADER_INTERNAL_TOKEN) String internalToken,
                    @RequestBody Map<String, Object> body);
}
