package com.mallx.mgr.controller;

import com.mallx.common.result.R;
import com.mallx.mgr.dto.AuditReq;
import com.mallx.mgr.dto.ChannelConfigReq;
import com.mallx.mgr.dto.LoginReq;
import com.mallx.mgr.dto.MchRateReq;
import com.mallx.mgr.service.AdminService;
import com.mallx.mgr.service.FundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** 后台操作员账号创建（用户管理） */
    @PostMapping("/user/create")
    public R createUser(@RequestBody Map<String, Object> req) {
        return adminService.createUser(strOf(req, "username"), strOf(req, "password"),
                strOf(req, "realName"), strOf(req, "role"));
    }

    /** 后台操作员账号列表 */
    @PostMapping("/user/list")
    public R listUsers(@RequestBody Map<String, Object> req) {
        return adminService.listUsers(intOf(req, "pageNum", 1), intOf(req, "pageSize", 10));
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
