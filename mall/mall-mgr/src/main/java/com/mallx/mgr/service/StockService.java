package com.mallx.mgr.service;

import com.mallx.common.constant.MallConstant;
import com.mallx.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Redis+Lua 库存原子扣减服务：
 * 扣减/回补均通过 Lua 脚本在 Redis 端原子执行，避免"查询-判断-扣减"竞态导致超卖；
 * Redis 无库存Key时以数据库库存初始化，下单成功后同步扣减数据库库存（条件更新防超卖）。
 */
@Slf4j
@Service
public class StockService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> deductScript;
    private final DefaultRedisScript<Long> restoreScript;

    public StockService(StringRedisTemplate redisTemplate,
                        @Value("classpath:lua/stock_deduct.lua") ClassPathResource deductResource,
                        @Value("classpath:lua/stock_restore.lua") ClassPathResource restoreResource) {
        this.redisTemplate = redisTemplate;
        this.deductScript = loadScript(deductResource);
        this.restoreScript = loadScript(restoreResource);
    }

    /** 原子扣减，库存不足抛出业务异常 */
    public void deduct(String goodsNum, int count, int dbStock) {
        Long result = redisTemplate.execute(deductScript,
                List.of(MallConstant.KEY_STOCK + goodsNum),
                String.valueOf(count), String.valueOf(dbStock));
        if (result == null || result < 0) {
            throw new BizException("商品[" + goodsNum + "]库存不足");
        }
    }

    /** 原子回补（关单/失败回滚） */
    public void restore(String goodsNum, int count) {
        redisTemplate.execute(restoreScript, List.of(MallConstant.KEY_STOCK + goodsNum), String.valueOf(count));
    }

    /** 商品库存变更后与数据库同步 */
    public void syncStock(String goodsNum, int dbStock) {
        redisTemplate.opsForValue().set(MallConstant.KEY_STOCK + goodsNum, String.valueOf(dbStock));
    }

    private DefaultRedisScript<Long> loadScript(ClassPathResource resource) {
        try (var in = resource.getInputStream()) {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            script.setResultType(Long.class);
            return script;
        } catch (Exception e) {
            throw new IllegalStateException("加载Lua脚本失败: " + resource.getPath(), e);
        }
    }
}
