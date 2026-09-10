package com.mallx.alipay.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 商户订单列表分页请求（接口文档 4.3.2）。
 */
@Data
public class PayListReq {

    @NotBlank(message = "shopNum不能为空")
    private String shopNum;

    @Min(1)
    private Integer pageNum = 1;

    @Min(1)
    @Max(200)
    private Integer pageSize = 10;

    /** 订单状态筛选，不传查全部 */
    private Integer orderState;

    /** 支付方式筛选 */
    private String wayCode;

    private String sign;
}
