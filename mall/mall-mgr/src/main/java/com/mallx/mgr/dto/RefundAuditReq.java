package com.mallx.mgr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 平台退款审核请求。
 */
@Data
public class RefundAuditReq {

    @NotBlank(message = "refundOrderId不能为空")
    private String refundOrderId;

    /** 审核结论 1-通过（执行渠道退款） 2-驳回 */
    @NotNull(message = "auditState不能为空")
    private Integer auditState;

    private String auditRemark;

    private String sign;
}
