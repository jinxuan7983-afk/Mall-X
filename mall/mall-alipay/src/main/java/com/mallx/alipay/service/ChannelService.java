package com.mallx.alipay.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mallx.alipay.entity.ChannelConfig;
import com.mallx.alipay.mapper.ChannelConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支付渠道配置服务：本地缓存渠道配置，避免高频下单重复查库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelService {

    private static final long CACHE_TTL_MS = 60_000L;

    private final ChannelConfigMapper channelConfigMapper;
    private final ObjectMapper objectMapper;

    private final Map<String, CachedConfig> cache = new ConcurrentHashMap<>();

    public ChannelConfig get(String wayCode) {
        CachedConfig cached = cache.get(wayCode);
        if (cached != null && System.currentTimeMillis() - cached.time < CACHE_TTL_MS) {
            return cached.config;
        }
        ChannelConfig config = channelConfigMapper.selectOne(
                Wrappers.<ChannelConfig>lambdaQuery().eq(ChannelConfig::getWayCode, wayCode));
        if (config != null) {
            cache.put(wayCode, new CachedConfig(config, System.currentTimeMillis()));
        } else {
            cache.remove(wayCode);
        }
        return config;
    }

    /** 渠道配置变更后主动清缓存（供管理端调用） */
    public void evict(String wayCode) {
        cache.remove(wayCode);
    }

    /** 是否启用支付宝沙箱真实通道：私钥非空即启用，否则走内置模拟收银台 */
    public boolean useRealAlipay(ChannelConfig config) {
        return config != null && config.getPrivateKey() != null && !config.getPrivateKey().isBlank();
    }

    public Map<String, Object> toPublicMap(ChannelConfig config) {
        Map<String, Object> map = objectMapper.convertValue(config, Map.class);
        map.remove("privateKey");
        map.remove("alipayPublicKey");
        return map;
    }

    public LocalDateTime now() {
        return LocalDateTime.now();
    }

    private record CachedConfig(ChannelConfig config, long time) {
    }
}
