package com.mallx.alipay.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 支付结果回调请求（接口文档 4.2.2）。
 */
@Data
public class CallbackReq {

    @NotBlank(message = "payOrderId不能为空")
    private String payOrderId;

    @NotBlank(message = "channelOrderNo不能为空")
    private String channelOrderNo;

    /** 支付状态 0-成功 1-失败 */
    @NotBlank(message = "payState不能为空")
    private String payState;

    /** 第三方支付完成时间 yyyy-MM-dd HH:mm:ss */
    @NotBlank(message = "payTime不能为空")
    private String payTime;

    /** 渠道回调签名 */
    private String sign;
}
