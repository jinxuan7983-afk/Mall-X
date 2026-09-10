package com.mallx.alipay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 统一支付下单请求（接口文档 4.2.1）。
 */
@Data
public class PayCreateReq {

    @NotBlank(message = "不能为空")
    private String shopNum;

    @NotBlank(message = "不能为空")
    private String shopName;

    @NotBlank(message = "不能为空")
    private String appId;

    /** 支付方式编码 WECHAT/ALIPAY/UNIONPAY */
    @NotBlank(message = "不能为空")
    private String wayCode;

    /** 交易支付金额(元) */
    @NotNull(message = "不能为空")
    @DecimalMin(value = "0.01", message = "必须大于0")
    private BigDecimal payMoney;

    @NotBlank(message = "不能为空")
    private String subject;

    @NotBlank(message = "不能为空")
    private String userNum;

    /** 商户侧唯一订单号 */
    @NotBlank(message = "不能为空")
    private String orderNum;

    private String sign;
}
