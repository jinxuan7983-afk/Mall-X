package com.mallx.mgr.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 商户发货请求。
 */
@Data
public class DeliverReq {

    @NotBlank(message = "orderNum不能为空")
    private String orderNum;

    @NotBlank(message = "logisticsCompany不能为空")
    private String logisticsCompany;

    @NotBlank(message = "logisticsNo不能为空")
    private String logisticsNo;

    private String sign;
}
