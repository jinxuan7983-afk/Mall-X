package com.mallx.mgr.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商户费率配置请求（接口文档 4.5.3）。
 */
@Data
public class MchRateReq {

    @NotBlank(message = "shopNum不能为空")
    private String shopNum;

    @NotBlank(message = "wayCode不能为空")
    private String wayCode;

    /** 商户差异化费率，如0.0040，优先级高于渠道基础费率 */
    @NotNull(message = "mchRate不能为空")
    @DecimalMin(value = "0", message = "费率不能为负")
    @DecimalMax(value = "1", message = "费率不能超过100%")
    private BigDecimal mchRate;

    private String sign;
}
