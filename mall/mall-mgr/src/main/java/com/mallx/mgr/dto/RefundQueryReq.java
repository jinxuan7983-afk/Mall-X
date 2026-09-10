package com.mallx.mgr.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 退款结果查询请求（接口文档 4.4.2）。
 */
@Data
public class RefundQueryReq {

    @NotBlank(message = "refundOrderId不能为空")
    private String refundOrderId;

    @NotBlank(message = "shopNum不能为空")
    private String shopNum;

    private String sign;
}
