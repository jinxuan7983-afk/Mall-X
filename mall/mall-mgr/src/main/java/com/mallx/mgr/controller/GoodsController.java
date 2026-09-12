package com.mallx.mgr.controller;

import com.mallx.common.result.R;
import com.mallx.mgr.dto.GoodsReq;
import com.mallx.mgr.service.GoodsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 商品接口：商户发布/编辑，用户浏览（商品微服务能力并入 mall-mgr）。
 */
@RestController
@RequestMapping("/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final GoodsService goodsService;

    /** 商户发布商品 */
    @PostMapping("/create")
    public R create(@Valid @RequestBody GoodsReq req) {
        return goodsService.create(req);
    }

    /** 商户编辑商品（价格/库存/上下架） */
    @PostMapping("/update")
    public R update(@Valid @RequestBody GoodsReq req) {
        return goodsService.update(req);
    }

    /** 商户删除自有商品（仅商品归属商户可执行；被订单引用时禁止删除） */
    @PostMapping("/delete")
    public R delete(@RequestBody Map<String, Object> req,
                    @RequestHeader(value = "X-Mch-Token", required = false) String operatorToken) {
        return goodsService.mchDelete(strOf(req, "goodsNum"), operatorToken);
    }

    /** 商品分页列表 */
    @PostMapping("/list")
    public R list(@RequestBody(required = false) Map<String, Object> req) {
        java.util.Map<String, Object> q = req == null ? Map.of() : req;
        return goodsService.list(
                intOf(q, "pageNum", 1), intOf(q, "pageSize", 10),
                strOf(q, "goodsName"), strOf(q, "shopNum"),
                longOrNull(q, "categoryId"), intOrNull(q, "goodsState"));
    }

    /** 商品详情（Redis 缓存） */
    @PostMapping("/detail")
    public R detail(@RequestBody Map<String, Object> req) {
        return goodsService.detail(strOf(req, "goodsNum"));
    }

    private int intOf(Map<String, Object> q, String key, int def) {
        Object v = q.get(key);
        return v == null ? def : Integer.parseInt(String.valueOf(v));
    }

    private Integer intOrNull(Map<String, Object> q, String key) {
        Object v = q.get(key);
        return v == null || String.valueOf(v).isBlank() ? null : Integer.parseInt(String.valueOf(v));
    }

    private Long longOrNull(Map<String, Object> q, String key) {
        Object v = q.get(key);
        return v == null || String.valueOf(v).isBlank() ? null : Long.parseLong(String.valueOf(v));
    }

    private String strOf(Map<String, Object> q, String key) {
        Object v = q.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
