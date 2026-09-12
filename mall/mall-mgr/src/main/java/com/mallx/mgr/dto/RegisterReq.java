package com.mallx.mgr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 商户注册请求（接口文档 4.1.1）。
 * 字段长度与 t_mch 表列长对齐，格式校验前置到 DTO，避免脏数据直达数据库。
 */
@Data
public class RegisterReq {

    @NotBlank(message = "shopName不能为空")
    @Size(max = 64, message = "shopName长度不能超过64")
    private String shopName;

    @NotBlank(message = "shopPhone不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "shopPhone需为11位有效手机号")
    private String shopPhone;

    @NotBlank(message = "shopAddress不能为空")
    @Size(max = 255, message = "shopAddress长度不能超过255")
    private String shopAddress;

    @NotBlank(message = "username不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "username需为4~20位字母、数字或下划线")
    private String username;

    @NotBlank(message = "password不能为空")
    @Size(min = 6, max = 20, message = "password长度需为6~20位")
    private String password;

    @NotBlank(message = "验证码不能为空")
    private String captchaKey;

    @NotBlank(message = "验证码不能为空")
    private String captchaCode;

    private String sign;
}
