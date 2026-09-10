package com.mallx.mgr;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Mall-X 业务微服务：商户/平台管理/退款/统计/商品订单/资金等业务能力。
 */
@EnableAsync
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.mallx.mgr.feign")
@SpringBootApplication
@ComponentScan(basePackages = "com.mallx")
@ConfigurationPropertiesScan(basePackages = "com.mallx")
@MapperScan("com.mallx.mgr.mapper")
public class MallMgrApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallMgrApplication.class, args);
    }
}
