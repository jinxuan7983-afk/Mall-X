package com.mallx.mgr.controller;

import com.mallx.common.result.R;
import com.mallx.mgr.dto.RefundApplyReq;
import com.mallx.mgr.dto.RefundAuditReq;
import com.mallx.mgr.dto.RefundCreateReq;
import com.mallx.mgr.dto.RefundMchAuditReq;
import com.mallx.mgr.dto.RefundQueryReq;
import com.mallx.mgr.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 退款/售后接口：商户发起与查询（4.4）、用户端申请与进度、商户端审核与统计、平台退款审核。
 */
@RestController
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    /** 4.4.1 商户发起退款接口（商户代客发起） */
    @PostMapping("/refund/create")
    public R create(@Valid @RequestBody RefundCreateReq req) {
        return refundService.create(req);
    }

    /** 4.4.2 退款结果查询接口 */
    @PostMapping("/refund/query")
    public R query(@Valid @RequestBody RefundQueryReq req) {
        return refundService.query(req);
    }

    /* ---------- 用户端：退款申请与进度 ---------- */

    /** 用户端申请退款（订单维度，填写原因并可上传凭证；仅本人已支付订单） */
    @PostMapping("/refund/apply")
    public R apply(@Valid @RequestBody RefundApplyReq req) {
        return refundService.applyByUser(req);
    }

    /** 用户端：我的退款记录（含进度状态） */
    @PostMapping("/refund/user/list")
    public R userList(@RequestBody Map<String, Object> req) {
        return refundService.userList(strOf(req.get("userNum")),
                intOf(req.get("pageNum"), 1), intOf(req.get("pageSize"), 10));
    }

    /** 退款进度详情（用户按 userNum / 商户按 shopNum 校验归属，可只传其一） */
    @PostMapping("/refund/detail")
    public R detail(@RequestBody Map<String, Object> req) {
        return refundService.detail(strOf(req.get("refundOrderId")),
                strOf(req.get("userNum")), strOf(req.get("shopNum")));
    }

    /** 上传退款凭证（base64 图片，返回可访问地址；申请时随 evidence 数组提交） */
    @PostMapping("/refund/evidence/upload")
    public R uploadEvidence(@RequestBody Map<String, Object> req) {
        return refundService.uploadEvidence(strOf(req.get("imageBase64")));
    }

    /* ---------- 商户端：退款处理与统计 ---------- */

    /** 商户端：本商户退款单列表（支持状态筛选） */
    @PostMapping("/refund/mch/list")
    public R mchList(@RequestBody Map<String, Object> req) {
        Integer refundState = req.get("refundState") == null ? null : intOf(req.get("refundState"), null);
        return refundService.mchList(strOf(req.get("shopNum")),
                intOf(req.get("pageNum"), 1), intOf(req.get("pageSize"), 10), refundState);
    }

    /** 商户端：审核退款申请（同意→执行渠道退款；拒绝→驳回），身份经请求头 X-Mch-Token 校验 */
    @PostMapping("/refund/mch/audit")
    public R mchAudit(@Valid @RequestBody RefundMchAuditReq req,
                      @RequestHeader(value = "X-Mch-Token", required = false) String operatorToken) {
        return refundService.mchAudit(req, operatorToken);
    }

    /** 商户端：退款统计概览（各状态笔数/金额） */
    @PostMapping("/refund/mch/stats")
    public R mchStats(@RequestBody Map<String, Object> req) {
        return refundService.mchStats(strOf(req.get("shopNum")));
    }

    /** 平台退款审核（退款订单管控） */
    @PostMapping("/admin/refund/audit")
    public R audit(@Valid @RequestBody RefundAuditReq req) {
        return refundService.audit(req);
    }

    /** 平台退款列表分页 */
    @PostMapping("/admin/refund/list")
    public R list(@RequestBody java.util.Map<String, Object> req) {
        int pageNum = intOf(req.get("pageNum"), 1);
        int pageSize = intOf(req.get("pageSize"), 10);
        String shopNum = strOf(req.get("shopNum"));
        Integer refundState = req.get("refundState") == null ? null : intOf(req.get("refundState"), null);
        return refundService.list(pageNum, pageSize, shopNum, refundState);
    }

    /** 平台删除退款单（仅 ADMIN；仅退款失败可删，审核中/成功须留痕） */
    @PostMapping("/admin/refund/delete")
    public R delete(@RequestBody java.util.Map<String, Object> req,
                    @RequestHeader(value = "X-Admin-Token", required = false) String operatorToken) {
        return refundService.deleteRefund(strOf(req.get("refundOrderId")), operatorToken);
    }

    private int intOf(Object v, int def) {
        return v == null ? def : Integer.parseInt(String.valueOf(v));
    }

    private Integer intOf(Object v, Integer def) {
        return v == null ? def : Integer.parseInt(String.valueOf(v));
    }

    private String strOf(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
