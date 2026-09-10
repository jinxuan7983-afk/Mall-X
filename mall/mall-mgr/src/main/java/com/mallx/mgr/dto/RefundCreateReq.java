package com.mallx.mgr.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商户发起退款请求（接口文档 4.4.1）。
 */
@Data
public class RefundCreateReq {

    @NotBlank(message = "payOrderId不能为空")
    private String payOrderId;

    @NotNull(message = "refundMoney不能为空")
    @DecimalMin(value = "0.01", message = "退款金额必须大于0")
    private BigDecimal refundMoney;

    @NotBlank(message = "refundReason不能为空")
    private String refundReason;

    @NotBlank(message = "shopNum不能为空")
    private String shopNum;

    private String sign;
}
