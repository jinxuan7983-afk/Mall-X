package com.mallx.mgr.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 订单列表查询：userNum 查用户订单 / shopNum 查商户订单。
 */
@Data
public class OrderListReq {

    private String userNum;

    private String shopNum;

    @Min(1)
    private Integer pageNum = 1;

    @Min(1)
    @Max(200)
    private Integer pageSize = 10;

    private Integer orderState;

    private String sign;
}
