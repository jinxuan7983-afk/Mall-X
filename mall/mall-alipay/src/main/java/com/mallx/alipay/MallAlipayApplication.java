package com.mallx.alipay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.mybatis.spring.annotation.MapperScan;

/**
 * Mall-X 支付微服务：接通第三方支付渠道（支付宝沙箱），提供统一支付下单、回调处理、订单查询能力。
 */
@EnableDiscoveryClient
@SpringBootApplication
@ComponentScan(basePackages = "com.mallx")
@ConfigurationPropertiesScan(basePackages = "com.mallx")
@MapperScan("com.mallx.alipay.mapper")
public class MallAlipayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallAlipayApplication.class, args);
    }
}
