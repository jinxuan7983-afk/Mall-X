package com.mallx.mgr.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商户提现申请。
 */
@Data
public class WithdrawReq {

    @NotBlank(message = "shopNum不能为空")
    private String shopNum;

    @NotNull(message = "money不能为空")
    @DecimalMin(value = "0.01", message = "提现金额必须大于0")
    private BigDecimal money;

    private String sign;
}
