package com.mallx.mgr.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 商户交易统计请求（接口文档 4.6.2）。
 */
@Data
public class StatisMchReq {

    @NotBlank(message = "shopNum不能为空")
    private String shopNum;

    /** 统计起始时间 yyyy-MM-dd HH:mm:ss，不传默认近7天 */
    private String startTime;

    /** 统计结束时间 */
    private String endTime;

    private String sign;
}
