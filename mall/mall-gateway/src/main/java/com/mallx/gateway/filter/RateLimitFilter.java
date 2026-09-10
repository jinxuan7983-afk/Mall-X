package com.mallx.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * 高并发防护：基于 Redis+Lua 脚本的原子限流（固定窗口）。
 * 全局 QPS 与单 IP QPS 双维度限流，超限统一返回固定结果，保障系统高可用。
 * Redis 不可用时放行（fail-open），避免限流组件故障影响业务可用性。
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final DefaultRedisScript<Long> SCRIPT;

    static {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("""
                local current = redis.call('INCR', KEYS[1])
                if current == 1 then
                    redis.call('PEXPIRE', KEYS[1], ARGV[2])
                end
                if current > tonumber(ARGV[1]) then
                    return 0
                end
                return 1
                """);
        script.setResultType(Long.class);
        SCRIPT = script;
    }

    private static final String LIMIT_MESSAGE = "{\"message\":\"系统繁忙，请稍后再试\"}";

    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${mallx.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${mallx.rate-limit.global-qps:1000}")
    private long globalQps;

    @Value("${mallx.rate-limit.ip-qps:200}")
    private long ipQps;

    public RateLimitFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }
        long second = Instant.now().getEpochSecond();
        String ip = resolveIp(exchange.getRequest());
        return tryAcquire("ratelimit:global:" + second, globalQps)
                .flatMap(globalOk -> globalOk
                        ? tryAcquire("ratelimit:ip:" + ip + ":" + second, ipQps)
                        : Mono.just(false))
                .flatMap(ok -> ok ? chain.filter(exchange) : reject(exchange));
    }

    private Mono<Boolean> tryAcquire(String key, long limit) {
        return redisTemplate.execute(SCRIPT, List.of(key), List.of(String.valueOf(limit), "1000"))
                .next()
                .map(v -> v != null && v >= 1L)
                .onErrorResume(e -> {
                    log.warn("限流脚本执行异常，放行请求: {}", e.getMessage());
                    return Mono.just(true);
                });
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(org.springframework.http.HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(LIMIT_MESSAGE.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Flux.just(buffer));
    }

    private String resolveIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
