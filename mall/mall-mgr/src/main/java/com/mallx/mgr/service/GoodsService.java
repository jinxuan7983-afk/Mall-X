package com.mallx.mgr.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mallx.common.constant.MallConstant;
import com.mallx.common.exception.BizException;
import com.mallx.common.result.R;
import com.mallx.common.util.OrderNoUtil;
import com.mallx.mgr.dto.GoodsReq;
import com.mallx.mgr.entity.Goods;
import com.mallx.mgr.mapper.GoodsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 商品服务：商品发布/编辑/查询。商品详情走 Redis 缓存（缓存优化，支撑高并发读）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final GoodsMapper goodsMapper;
    private final StockService stockService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public R create(GoodsReq req) {
        if (req.getStock() == null || req.getStock() < 0) {
            throw new BizException("库存不合法");
        }
        Goods goods = new Goods();
        goods.setGoodsNum(OrderNoUtil.goodsNum());
        goods.setGoodsName(req.getGoodsName());
        goods.setShopNum(req.getShopNum());
        goods.setCategoryId(req.getCategoryId());
        goods.setPrice(req.getPrice());
        goods.setStock(req.getStock());
        goods.setSales(0);
        goods.setGoodsState(req.getGoodsState() == null ? 1 : req.getGoodsState());
        goods.setPicUrl(req.getPicUrl());
        goods.setDetail(req.getDetail());
        goodsMapper.insert(goods);
        stockService.syncStock(goods.getGoodsNum(), goods.getStock());
        return R.ok("商品发布成功").set("goodsNum", goods.getGoodsNum());
    }

    public R update(GoodsReq req) {
        if (StringUtils.hasText(req.getGoodsNum())) {
            Goods goods = goodsMapper.selectOne(Wrappers.<Goods>lambdaQuery()
                    .eq(Goods::getGoodsNum, req.getGoodsNum()));
            if (goods == null) {
                throw new BizException("商品不存在");
            }
            if (!goods.getShopNum().equals(req.getShopNum())) {
                throw new BizException("无权操作其他商户商品");
            }
            if (StringUtils.hasText(req.getGoodsName())) {
                goods.setGoodsName(req.getGoodsName());
            }
            if (req.getPrice() != null) {
                goods.setPrice(req.getPrice());
            }
            if (req.getCategoryId() != null) {
                goods.setCategoryId(req.getCategoryId());
            }
            if (req.getGoodsState() != null) {
                goods.setGoodsState(req.getGoodsState());
            }
            if (req.getPicUrl() != null) {
                goods.setPicUrl(req.getPicUrl());
            }
            if (req.getDetail() != null) {
                goods.setDetail(req.getDetail());
            }
            goodsMapper.updateById(goods);
            if (req.getStock() != null) {
                // 库存调整：数据库 + Redis 同步
                goods.setStock(req.getStock());
                Goods db = new Goods();
                db.setId(goods.getId());
                db.setStock(req.getStock());
                goodsMapper.updateById(db);
                stockService.syncStock(goods.getGoodsNum(), req.getStock());
            }
            evictCache(goods.getGoodsNum());
            return R.ok("商品更新成功");
        }
        throw new BizException("编辑商品必须传goodsNum");
    }

    /** 商品分页查询（面向用户浏览与商户管理） */
    public R list(int pageNum, int pageSize, String goodsName, String shopNum, Long categoryId, Integer goodsState) {
        Page<Goods> page = goodsMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Goods>lambdaQuery()
                        .like(StringUtils.hasText(goodsName), Goods::getGoodsName, goodsName)
                        .eq(StringUtils.hasText(shopNum), Goods::getShopNum, shopNum)
                        .eq(categoryId != null, Goods::getCategoryId, categoryId)
                        .eq(goodsState != null, Goods::getGoodsState, goodsState)
                        .orderByDesc(Goods::getId));
        R r = R.ok("查询成功");
        r.set("total", page.getTotal());
        r.set("pages", page.getPages());
        r.set("current", page.getCurrent());
        r.set("size", page.getSize());
        r.set("records", page.getRecords());
        return r;
    }

    /** 商品详情：Cache-Aside 模式，先查 Redis，未命中查库并回填 */
    public R detail(String goodsNum) {
        String key = MallConstant.KEY_GOODS_CACHE + goodsNum;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return R.ok("查询成功").set("goods", objectMapper.readValue(cached, Goods.class));
            } catch (Exception e) {
                log.warn("商品缓存反序列化失败，回源数据库 goodsNum={}", goodsNum);
            }
        }
        Goods goods = goodsMapper.selectOne(Wrappers.<Goods>lambdaQuery().eq(Goods::getGoodsNum, goodsNum));
        if (goods == null) {
            throw new BizException("商品不存在");
        }
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(goods), CACHE_TTL);
        } catch (Exception e) {
            log.warn("商品缓存写入失败 goodsNum={}", goodsNum);
        }
        return R.ok("查询成功").set("goods", goods);
    }

    private void evictCache(String goodsNum) {
        redisTemplate.delete(MallConstant.KEY_GOODS_CACHE + goodsNum);
    }
}
