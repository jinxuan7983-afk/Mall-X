package com.mallx.alipay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付渠道配置。
 */
@Data
@TableName("t_channel_config")
public class ChannelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String wayCode;

    private String channelName;

    /** 1-开启 0-关闭 */
    private Integer switchStatus;

    /** 渠道基础费率 */
    private BigDecimal baseRate;

    private String appId;

    private String privateKey;

    private String alipayPublicKey;

    private String gatewayUrl;

    private String notifyKey;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
