package com.mallx.mgr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求：用户名 4~20 位字母/数字/下划线，密码 6~20 位，
 * 附图形验证码（一次性，Redis 校验）。
 */
@Data
public class UserRegisterReq {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "用户名需为4~20位字母、数字或下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需为6~20位")
    private String password;

    @NotBlank(message = "验证码不能为空")
    private String captchaKey;

    @NotBlank(message = "验证码不能为空")
    private String captchaCode;

    private String sign;
}
