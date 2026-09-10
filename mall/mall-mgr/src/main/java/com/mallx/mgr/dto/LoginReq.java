package com.mallx.mgr.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求（商户 4.1.2 / 平台管理员 / 用户通用）。
 */
@Data
public class LoginReq {

    @NotBlank(message = "username不能为空")
    private String username;

    @NotBlank(message = "password不能为空")
    private String password;
}
