package com.mallx.mgr.controller;

import com.mallx.common.result.R;
import com.mallx.mgr.dto.AuditReq;
import com.mallx.mgr.dto.ChannelConfigReq;
import com.mallx.mgr.dto.LoginReq;
import com.mallx.mgr.dto.MchRateReq;
import com.mallx.mgr.service.AdminService;
import com.mallx.mgr.service.FundService;
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
 * 平台管理端接口（接口文档 4.5 + 系统运维管理）。
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final FundService fundService;
    private final GoodsService goodsService;

    /** 全平台商品列表（名称/商户/类目/状态筛选） */
    @PostMapping("/goods/list")
    public R goodsList(@RequestBody(required = false) Map<String, Object> req) {
        Map<String, Object> q = req == null ? Map.of() : req;
        return goodsService.list(
                intOf(q, "pageNum", 1), intOf(q, "pageSize", 10),
                strOf(q, "goodsName"), strOf(q, "shopNum"),
                longOrNull(q, "categoryId"), intOrNull(q, "goodsState"));
    }

    /** 平台管控：强制商品上下架 */
    @PostMapping("/goods/state")
    public R goodsState(@RequestBody Map<String, Object> req) {
        return goodsService.adminUpdateState(strOf(req, "goodsNum"), intOrNull(req, "goodsState"));
    }

    private Long longOrNull(Map<String, Object> q, String key) {
        Object v = q.get(key);
        return v == null || String.valueOf(v).isBlank() ? null : Long.parseLong(String.valueOf(v));
    }

    /** 管理员登录 */
    @PostMapping("/login")
    public R login(@Valid @RequestBody LoginReq req) {
        return adminService.login(req);
    }

    /** 4.5.1 商户审核接口 */
    @PostMapping("/mch/audit")
    public R auditMch(@Valid @RequestBody AuditReq req) {
        return adminService.auditMch(req);
    }

    /** 商户列表分页查询 */
    @PostMapping("/mch/list")
    public R listMch(@RequestBody Map<String, Object> req) {
        return adminService.listMch(
                intOf(req, "pageNum", 1), intOf(req, "pageSize", 10),
                strOf(req, "shopName"), intOrNull(req, "shopState"));
    }

    /** 4.5.3 商户费率配置接口 */
    @PostMapping("/mch/rate")
    public R configRate(@Valid @RequestBody MchRateReq req) {
        return adminService.configMchRate(req);
    }

    /** 4.5.2 支付渠道配置接口 */
    @PostMapping("/channel/config")
    public R configChannel(@Valid @RequestBody ChannelConfigReq req) {
        return adminService.configChannel(req);
    }

    /** 渠道配置列表 */
    @PostMapping("/channel/list")
    public R listChannels() {
        return adminService.listChannels();
    }

    /** 全平台支付订单查询（全局订单管控） */
    @PostMapping("/pay/list")
    public R listPayOrders(@RequestBody Map<String, Object> req) {
        return adminService.listPayOrders(
                intOf(req, "pageNum", 1), intOf(req, "pageSize", 10),
                strOf(req, "shopNum"), intOrNull(req, "orderState"),
                strOf(req, "wayCode"), strOf(req, "startTime"), strOf(req, "endTime"));
    }

    /** 提现审核 */
    @PostMapping("/withdraw/audit")
    public R withdrawAudit(@RequestBody Map<String, Object> req) {
        return fundService.withdrawAudit(strOf(req, "withdrawNo"),
                intOrNull(req, "state"), strOf(req, "remark"));
    }

    /** 提现记录列表 */
    @PostMapping("/withdraw/list")
    public R withdrawList(@RequestBody Map<String, Object> req) {
        return fundService.withdrawList(strOf(req, "shopNum"),
                intOf(req, "pageNum", 1), intOf(req, "pageSize", 10));
    }

    /** 商户删除（仅 ADMIN；存在商品/订单/资金等业务数据时禁止） */
    @PostMapping("/mch/delete")
    public R deleteMch(@RequestBody Map<String, Object> req,
                       @RequestHeader(value = "X-Admin-Token", required = false) String operatorToken) {
        return adminService.deleteMch(strOf(req, "shopNum"), operatorToken);
    }

    /** 全平台商品删除（仅 ADMIN；被订单引用时禁止） */
    @PostMapping("/goods/delete")
    public R deleteGoods(@RequestBody Map<String, Object> req,
                         @RequestHeader(value = "X-Admin-Token", required = false) String operatorToken) {
        return goodsService.adminDelete(strOf(req, "goodsNum"), operatorToken);
    }

    /** 商品图片上传/替换（仅 ADMIN；base64 图片，更新后商品详情缓存即时失效） */
    @PostMapping("/goods/pic")
    public R updateGoodsPic(@RequestBody Map<String, Object> req,
                            @RequestHeader(value = "X-Admin-Token", required = false) String operatorToken) {
        return goodsService.updateGoodsPic(strOf(req, "goodsNum"), strOf(req, "imageBase64"), operatorToken);
    }

    /** 支付订单删除（仅 ADMIN；仅支付失败/关闭/待支付可删） */
    @PostMapping("/pay/delete")
    public R deletePay(@RequestBody Map<String, Object> req,
                       @RequestHeader(value = "X-Admin-Token", required = false) String operatorToken) {
        return adminService.deletePayOrder(strOf(req, "payOrderId"), operatorToken);
    }

    /** 支付渠道配置删除（仅 ADMIN；须保留至少一个渠道） */
    @PostMapping("/channel/delete")
    public R deleteChannel(@RequestBody Map<String, Object> req,
                           @RequestHeader(value = "X-Admin-Token", required = false) String operatorToken) {
        return adminService.deleteChannel(strOf(req, "wayCode"), operatorToken);
    }

    /** 提现单删除（仅 ADMIN；仅已驳回记录可删） */
    @PostMapping("/withdraw/delete")
    public R deleteWithdraw(@RequestBody Map<String, Object> req,
                            @RequestHeader(value = "X-Admin-Token", required = false) String operatorToken) {
        return fundService.deleteWithdraw(strOf(req, "withdrawNo"), operatorToken);
    }

    /** 操作日志删除（仅 ADMIN；删除动作本身亦被审计切面记录） */
    @PostMapping("/log/delete")
    public R deleteLog(@RequestBody Map<String, Object> req,
                       @RequestHeader(value = "X-Admin-Token", required = false) String operatorToken) {
        return adminService.deleteLog(longOrNull(req, "id"), operatorToken);
    }

    /** 操作日志查询（系统运维管理） */
    @PostMapping("/log/list")
    public R listLogs(@RequestBody Map<String, Object> req) {
        return adminService.listLogs(intOf(req, "pageNum", 1), intOf(req, "pageSize", 10));
    }

    private int intOf(Map<String, Object> req, String key, int def) {
        Object v = req.get(key);
        return v == null ? def : Integer.parseInt(String.valueOf(v));
    }

    private Integer intOrNull(Map<String, Object> req, String key) {
        Object v = req.get(key);
        return v == null || String.valueOf(v).isBlank() ? null : Integer.parseInt(String.valueOf(v));
    }

    private String strOf(Map<String, Object> req, String key) {
        Object v = req.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
