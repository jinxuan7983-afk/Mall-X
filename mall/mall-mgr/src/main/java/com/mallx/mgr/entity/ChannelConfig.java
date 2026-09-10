package com.mallx.mgr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付渠道配置（平台管理端维护，支付微服务读取）。
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

    private BigDecimal baseRate;

    private String appId;

    private String privateKey;

    private String alipayPublicKey;

    private String gatewayUrl;

    private String notifyKey;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
