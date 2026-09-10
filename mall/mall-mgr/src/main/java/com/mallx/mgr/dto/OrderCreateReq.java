package com.mallx.mgr.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 用户下单请求：购物车结算，预扣库存 → 延迟消息关单。
 */
@Data
public class OrderCreateReq {

    @NotBlank(message = "userNum不能为空")
    private String userNum;

    /** 单个订单仅支持同一商户的商品，不同店铺请分开提交 */
    @NotBlank(message = "shopNum不能为空")
    private String shopNum;

    @NotEmpty(message = "items不能为空")
    @Valid
    private java.util.List<Item> items;

    @NotBlank(message = "receiverName不能为空")
    private String receiverName;

    @NotBlank(message = "receiverPhone不能为空")
    private String receiverPhone;

    @NotBlank(message = "receiverAddress不能为空")
    private String receiverAddress;

    private String sign;

    @Data
    public static class Item {

        @NotBlank(message = "goodsNum不能为空")
        private String goodsNum;

        @NotNull(message = "count不能为空")
        private Integer count;
    }
}
