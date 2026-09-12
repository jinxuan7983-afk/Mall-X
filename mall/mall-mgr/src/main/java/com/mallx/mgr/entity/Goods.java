package com.mallx.mgr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品。
 */
@Data
@TableName("t_goods")
public class Goods {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String goodsNum;

    private String goodsName;

    private String shopNum;

    /** 店铺名称（非数据库字段，查询时由商户表关联填充，前端展示用） */
    @TableField(exist = false)
    private String shopName;

    private Long categoryId;

    private BigDecimal price;

    /** 数据库库存（Redis 预扣后异步/同步落库） */
    private Integer stock;

    private Integer sales;

    /** 1-上架 0-下架 */
    private Integer goodsState;

    private String picUrl;

    private String detail;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
