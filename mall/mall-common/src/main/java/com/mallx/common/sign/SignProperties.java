package com.mallx.common.sign;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 签名验签配置项。
 */
@Data
@ConfigurationProperties(prefix = "mallx.sign")
public class SignProperties {

    /** 是否开启验签 */
    private boolean enabled = true;

    /** 签名密钥（与客户端约定，渠道回调共用该密钥） */
    private String secret = "mallx-pay-secret-2026";

    /** 不参与验签的路径（Ant 风格），如登录接口、内部 Feign 接口、模拟支付页等 */
    private List<String> excludePaths = new ArrayList<>();
}
