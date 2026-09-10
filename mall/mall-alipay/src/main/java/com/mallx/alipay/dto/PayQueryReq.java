package com.mallx.alipay.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 支付订单查询请求（接口文档 4.3.1）。
 */
@Data
public class PayQueryReq {

    @NotBlank(message = "shopNum不能为空")
    private String shopNum;

    @NotBlank(message = "appId不能为空")
    private String appId;

    /** 支付系统订单号 */
    @NotBlank(message = "payOrderNum不能为空")
    private String payOrderNum;

    private String sign;
}
