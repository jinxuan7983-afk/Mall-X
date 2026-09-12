package com.mallx.mgr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商户端退款审核请求（仅退款单归属商户可处理，身份由请求头 X-Mch-Token 校验）。
 */
@Data
public class RefundMchAuditReq {

    @NotBlank(message = "refundOrderId不能为空")
    private String refundOrderId;

    /** 审核结论 1-同意（执行渠道退款） 2-拒绝 */
    @NotNull(message = "auditState不能为空")
    private Integer auditState;

    /** 处理备注（拒绝原因/同意说明） */
    private String auditRemark;

    private String sign;
}
