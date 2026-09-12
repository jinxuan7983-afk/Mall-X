package com.mallx.mgr.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.mallx.common.sign.SignFilter;
import com.mallx.common.sign.SignProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 业务微服务配置：MyBatis-Plus 分页插件 + 统一签名验签过滤器。
 * （上传文件访问见 UploadsController，直接读取本地文件。）
 */
@Configuration
public class WebConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public SignFilter signFilter(SignProperties properties) {
        return new SignFilter(properties);
    }
}
