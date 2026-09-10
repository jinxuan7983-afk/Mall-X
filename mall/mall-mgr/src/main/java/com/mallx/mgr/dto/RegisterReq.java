package com.mallx.mgr.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 商户注册请求（接口文档 4.1.1）。
 */
@Data
public class RegisterReq {

    @NotBlank(message = "shopName不能为空")
    private String shopName;

    @NotBlank(message = "shopPhone不能为空")
    private String shopPhone;

    @NotBlank(message = "shopAddress不能为空")
    private String shopAddress;

    @NotBlank(message = "username不能为空")
    private String username;

    @NotBlank(message = "password不能为空")
    private String password;

    private String sign;
}
