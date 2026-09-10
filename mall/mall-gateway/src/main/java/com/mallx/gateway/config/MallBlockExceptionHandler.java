package com.mallx.gateway.config;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Sentinel 熔断降级统一兜底：被限流/熔断的请求统一返回固定 JSON 结果。
 */
public class MallBlockExceptionHandler extends SentinelGatewayBlockExceptionHandler {

    private static final String BLOCK_MESSAGE = "{\"message\":\"系统繁忙，请稍后再试\"}";

    public MallBlockExceptionHandler(List<ViewResolver> viewResolvers, ServerCodecConfigurer serverCodecConfigurer) {
        super(viewResolvers, serverCodecConfigurer);
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (BlockException.isBlockException(ex)) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(org.springframework.http.HttpStatus.OK);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = response.bufferFactory().wrap(BLOCK_MESSAGE.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Flux.just(buffer));
        }
        return super.handle(exchange, ex);
    }
}
