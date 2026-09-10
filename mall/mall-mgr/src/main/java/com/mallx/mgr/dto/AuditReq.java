package com.mallx.mgr.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商户审核请求（接口文档 4.5.1）。
 */
@Data
public class AuditReq {

    @jakarta.validation.constraints.NotBlank(message = "shopNum不能为空")
    private String shopNum;

    /** 审核状态 1-审核通过 2-审核驳回 */
    @NotNull(message = "shopState不能为空")
    private Integer shopState;

    /** 审核备注，驳回需填写原因 */
    private String auditRemark;

    private String sign;
}
