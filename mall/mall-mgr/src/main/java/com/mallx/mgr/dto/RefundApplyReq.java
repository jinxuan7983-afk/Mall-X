package com.mallx.mgr.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户端发起退款申请请求（按业务订单维度，支持部分退款，可附凭证图片）。
 */
@Data
public class RefundApplyReq {

    @NotBlank(message = "orderNum不能为空")
    private String orderNum;

    @NotBlank(message = "userNum不能为空")
    private String userNum;

    @NotNull(message = "refundMoney不能为空")
    @DecimalMin(value = "0.01", message = "退款金额必须大于0")
    private BigDecimal refundMoney;

    @NotBlank(message = "refundReason不能为空")
    private String refundReason;

    /** 凭证图片地址集合（先经 /refund/evidence/upload 上传获得；数组不参与签名） */
    private List<String> evidence;

    private String sign;
}
