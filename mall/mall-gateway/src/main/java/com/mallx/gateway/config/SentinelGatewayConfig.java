package com.mallx.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sentinel 网关维度的限流/熔断规则：对核心路由统一按 QPS 限流，高并发场景自动保护下游服务。
 */
@Configuration
public class SentinelGatewayConfig {

    @PostConstruct
    public void initGatewayRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();
        for (String resource : List.of("mall-mgr", "mall-alipay")) {
            GatewayFlowRule rule = new GatewayFlowRule(resource);
            rule.setCount(1000);      // QPS >= 1000
            rule.setIntervalSec(1);   // 统计窗口1秒
            rules.add(rule);
        }
        GatewayRuleManager.loadRules(rules);
    }

    @Bean
    public MallBlockExceptionHandler mallBlockExceptionHandler(List<ViewResolver> viewResolvers,
                                                               ServerCodecConfigurer serverCodecConfigurer) {
        return new MallBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
    }
}
