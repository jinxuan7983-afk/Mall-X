package com.mallx.mgr.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 渠道维度统计行（MyBatis 别名映射）。
 */
@Data
public class ChannelStat {

    private String wayCode;

    private Long count;

    private BigDecimal tradeMoney;

    private BigDecimal fee;
}
