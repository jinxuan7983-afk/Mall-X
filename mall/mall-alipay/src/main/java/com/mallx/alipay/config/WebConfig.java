package com.mallx.alipay.config;

import com.mallx.common.sign.SignFilter;
import com.mallx.common.sign.SignProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web 配置：注册统一签名验签过滤器（业务接口防篡改）。
 */
@Configuration
public class WebConfig {

    @Bean
    public SignFilter signFilter(SignProperties properties) {
        return new SignFilter(properties);
    }
}
