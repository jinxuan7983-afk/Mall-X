package com.mallx.mgr.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付渠道配置请求（接口文档 4.5.2）：渠道参数、开关、基础费率。
 */
@Data
public class ChannelConfigReq {

    @NotBlank(message = "wayCode不能为空")
    private String wayCode;

    private String channelName;

    /** 渠道开关 1-开启 0-关闭 */
    private Integer switchStatus;

    @DecimalMin(value = "0", message = "费率不能为负")
    @DecimalMax(value = "1", message = "费率不能超过100%")
    private BigDecimal baseRate;

    private String appId;

    private String privateKey;

    private String alipayPublicKey;

    private String gatewayUrl;

    private String sign;
}
