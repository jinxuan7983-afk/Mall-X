package com.mallx.mgr.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商户发布/编辑商品请求。
 */
@Data
public class GoodsReq {

    /** 编辑时必填 */
    private String goodsNum;

    @NotBlank(message = "goodsName不能为空")
    private String goodsName;

    @NotBlank(message = "shopNum不能为空")
    private String shopNum;

    private Long categoryId;

    @NotNull(message = "price不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    private BigDecimal price;

    @NotNull(message = "stock不能为空")
    private Integer stock;

    private Integer goodsState;

    private String picUrl;

    private String detail;

    private String sign;
}
