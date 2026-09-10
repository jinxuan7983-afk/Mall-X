package com.mallx.alipay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商户差异化费率（优先级高于渠道基础费率）。
 */
@Data
@TableName("t_mch_rate")
public class MchRate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String shopNum;

    private String wayCode;

    private BigDecimal mchRate;
}
