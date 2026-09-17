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
import com.mallx.mgr.entity.Mch;
import com.mallx.mgr.entity.OrderItem;
import com.mallx.mgr.mapper.GoodsMapper;
import com.mallx.mgr.mapper.MchMapper;
import com.mallx.mgr.mapper.OrderItemMapper;
import com.mallx.mgr.support.AdminGuard;
import com.mallx.mgr.support.MchGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 商品服务：商品发布/编辑/查询。商品详情走 Redis 缓存（缓存优化，支撑高并发读）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    /** 商品图片大小上限（解码后字节数） */
    private static final long MAX_PIC_BYTES = 2L * 1024 * 1024;

    /** 前端压缩后提交的 data URL 图片（仅允许 jpg/png/webp） */
    private static final Pattern DATA_URL = Pattern.compile("^data:image/(jpeg|png|webp);base64,(.+)$",
            Pattern.CASE_INSENSITIVE);

    @Value("${mallx.upload.dir:./uploads}")
    private String uploadDir;

    private final GoodsMapper goodsMapper;
    private final MchMapper mchMapper;
    private final OrderItemMapper orderItemMapper;
    private final StockService stockService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AdminGuard adminGuard;
    private final MchGuard mchGuard;

    public R create(GoodsReq req) {
        if (req.getStock() == null || req.getStock() < 0) {
            throw new BizException("库存不合法");
        }
        Goods goods = new Goods();
        goods.setGoodsName(req.getGoodsName());
        goods.setShopNum(req.getShopNum());
        goods.setCategoryId(req.getCategoryId());
        goods.setPrice(req.getPrice());
        goods.setStock(req.getStock());
        goods.setSales(0);
        goods.setGoodsState(req.getGoodsState() == null ? 1 : req.getGoodsState());
        goods.setPicUrl(req.getPicUrl());
        goods.setDetail(req.getDetail());
        // 短编号碰撞概率极低：唯一索引兜底，冲突时重新生成编号重试
        for (int attempt = 0; attempt < 3; attempt++) {
            goods.setGoodsNum(OrderNoUtil.goodsNum());
            try {
                goodsMapper.insert(goods);
                break;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                if (attempt == 2) {
                    throw new BizException("系统繁忙，请稍后重试");
                }
            }
        }
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

    /** 管理员强制上下架（平台管控，无需商户归属校验） */
    public R adminUpdateState(String goodsNum, Integer goodsState) {
        if (!StringUtils.hasText(goodsNum) || goodsState == null) {
            throw new BizException("goodsNum与goodsState不能为空");
        }
        if (goodsState != 0 && goodsState != 1) {
            throw new BizException("goodsState仅支持0-下架/1-上架");
        }
        Goods goods = goodsMapper.selectOne(Wrappers.<Goods>lambdaQuery().eq(Goods::getGoodsNum, goodsNum));
        if (goods == null) {
            throw new BizException("商品不存在");
        }
        Goods db = new Goods();
        db.setId(goods.getId());
        db.setGoodsState(goodsState);
        goodsMapper.updateById(db);
        evictCache(goodsNum);
        return R.ok(goodsState == 1 ? "商品已上架" : "商品已下架");
    }

    /**
     * 管理员删除商品（仅 ADMIN 可执行）。
     * 超级管理员不受限制：即使被订单引用也可删除（force=true 跳过引用校验）。
     */
    public R adminDelete(String goodsNum, String operatorToken) {
        adminGuard.requireAdmin(operatorToken);
        if (!StringUtils.hasText(goodsNum)) {
            throw new BizException("请指定要删除的商品");
        }
        Goods goods = goodsMapper.selectOne(Wrappers.<Goods>lambdaQuery().eq(Goods::getGoodsNum, goodsNum));
        if (goods == null) {
            throw new BizException("商品不存在或已被删除");
        }
        doDeleteGoods(goods, true);
        return R.ok("商品「" + goods.getGoodsName() + "」已删除");
    }

    /**
     * 商户删除自有商品（仅商品归属商户本人可执行）。
     * 删除策略与管理端一致：被订单引用时禁止删除（可改为下架）。
     */
    public R mchDelete(String goodsNum, String operatorToken) {
        String shopNum = mchGuard.requireMch(operatorToken);
        if (!StringUtils.hasText(goodsNum)) {
            throw new BizException("请指定要删除的商品");
        }
        Goods goods = goodsMapper.selectOne(Wrappers.<Goods>lambdaQuery().eq(Goods::getGoodsNum, goodsNum));
        if (goods == null) {
            throw new BizException("商品不存在或已被删除");
        }
        if (!goods.getShopNum().equals(shopNum)) {
            throw new BizException("无权操作其他商户的商品");
        }
        doDeleteGoods(goods, false);
        return R.ok("商品「" + goods.getGoodsName() + "」已删除");
    }

    /**
     * 删除商品：物理删除 → 清理详情缓存与 Redis 库存（管理端/商户端共用）。
     *
     * @param force true = 超级管理员，跳过订单引用校验；false = 商户自助删除，被订单引用时仍拒绝
     */
    private void doDeleteGoods(Goods goods, boolean force) {
        if (!force) {
            Long refs = orderItemMapper.selectCount(Wrappers.<OrderItem>lambdaQuery()
                    .eq(OrderItem::getGoodsNum, goods.getGoodsNum()));
            if (refs != null && refs > 0) {
                throw new BizException("该商品已被订单引用，为保证交易可追溯不可删除，可改为下架");
            }
        }
        goodsMapper.deleteById(goods.getId());
        evictCache(goods.getGoodsNum());
        redisTemplate.delete(MallConstant.KEY_STOCK + goods.getGoodsNum());
    }

    /**
     * 管理员上传/替换商品图片（仅 ADMIN 可执行）。
     * 入参为前端压缩后的 base64（data URL 或纯 base64），保存到本地上传目录并更新 pic_url，
     * 随后失效商品详情缓存，保证商城端与管理端刷新即见新图。
     */
    public R updateGoodsPic(String goodsNum, String imageBase64, String operatorToken) {
        adminGuard.requireAdmin(operatorToken);
        if (!StringUtils.hasText(goodsNum)) {
            throw new BizException("请指定要修改的商品");
        }
        if (!StringUtils.hasText(imageBase64)) {
            throw new BizException("请选择要上传的图片");
        }
        Goods goods = goodsMapper.selectOne(Wrappers.<Goods>lambdaQuery().eq(Goods::getGoodsNum, goodsNum));
        if (goods == null) {
            throw new BizException("商品不存在或已被删除");
        }
        String payload = imageBase64.trim();
        String ext = "jpg";
        Matcher matcher = DATA_URL.matcher(payload);
        if (matcher.matches()) {
            String type = matcher.group(1).toLowerCase();
            ext = "jpeg".equals(type) ? "jpg" : type;
            payload = matcher.group(2);
        } else if (payload.startsWith("data:")) {
            throw new BizException("仅支持 JPG/PNG/WebP 格式的图片");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(payload.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            throw new BizException("图片数据格式不正确");
        }
        if (bytes.length == 0) {
            throw new BizException("图片内容为空");
        }
        if (bytes.length > MAX_PIC_BYTES) {
            throw new BizException("图片大小不能超过 2MB");
        }
        try {
            Path dir = Paths.get(uploadDir, "goods").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String fileName = goodsNum + "_" + System.currentTimeMillis() + "." + ext;
            Files.write(dir.resolve(fileName), bytes);
            String picUrl = "/uploads/goods/" + fileName;
            Goods update = new Goods();
            update.setId(goods.getId());
            update.setPicUrl(picUrl);
            goodsMapper.updateById(update);
            evictCache(goodsNum);
            return R.ok("商品图片更新成功").set("picUrl", picUrl);
        } catch (IOException e) {
            log.error("商品图片保存失败 goodsNum={}", goodsNum, e);
            throw new BizException("图片保存失败，请稍后重试");
        }
    }

    /** 关联填充店铺名称（前端展示店铺名而非编号，非数据库字段） */
    private void fillShopName(java.util.List<Goods> goodsList) {
        if (goodsList == null || goodsList.isEmpty()) {
            return;
        }
        java.util.Set<String> shopNums = goodsList.stream()
                .map(Goods::getShopNum)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        if (shopNums.isEmpty()) {
            return;
        }
        java.util.Map<String, String> nameMap = mchMapper.selectList(
                        Wrappers.<Mch>lambdaQuery().in(Mch::getShopNum, shopNums))
                .stream()
                .collect(java.util.stream.Collectors.toMap(Mch::getShopNum, Mch::getShopName, (a, b) -> a));
        goodsList.forEach(g -> g.setShopName(nameMap.getOrDefault(g.getShopNum(), g.getShopNum())));
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
        fillShopName(page.getRecords());
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
                Goods cachedGoods = objectMapper.readValue(cached, Goods.class);
                fillShopName(java.util.List.of(cachedGoods));
                return R.ok("查询成功").set("goods", cachedGoods);
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
        fillShopName(java.util.List.of(goods));
        return R.ok("查询成功").set("goods", goods);
    }

    private void evictCache(String goodsNum) {
        redisTemplate.delete(MallConstant.KEY_GOODS_CACHE + goodsNum);
    }
}
