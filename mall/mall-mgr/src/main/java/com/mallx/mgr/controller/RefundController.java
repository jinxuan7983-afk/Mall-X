package com.mallx.mgr.controller;

import com.mallx.common.result.R;
import com.mallx.mgr.dto.RefundAuditReq;
import com.mallx.mgr.dto.RefundCreateReq;
import com.mallx.mgr.dto.RefundQueryReq;
import com.mallx.mgr.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退款管理接口（接口文档 4.4）+ 平台退款审核。
 */
@RestController
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    /** 4.4.1 商户发起退款接口 */
    @PostMapping("/refund/create")
    public R create(@Valid @RequestBody RefundCreateReq req) {
        return refundService.create(req);
    }

    /** 4.4.2 退款结果查询接口 */
    @PostMapping("/refund/query")
    public R query(@Valid @RequestBody RefundQueryReq req) {
        return refundService.query(req);
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
