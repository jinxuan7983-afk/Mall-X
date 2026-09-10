package com.mallx.mgr.controller;

import com.mallx.common.result.R;
import com.mallx.mgr.dto.WithdrawReq;
import com.mallx.mgr.service.FundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 商户资金管理：资金总览、提现申请、提现记录。
 */
@RestController
@RequestMapping("/fund")
@RequiredArgsConstructor
public class FundController {

    private final FundService fundService;

    /** 账户资金总览（总营收/手续费/净收益/可提现余额） */
    @PostMapping("/overview")
    public R overview(@RequestBody Map<String, Object> req) {
        Object shopNum = req.get("shopNum");
        if (shopNum == null || String.valueOf(shopNum).isBlank()) {
            throw new com.mallx.common.exception.BizException("shopNum不能为空");
        }
        return fundService.overview(String.valueOf(shopNum));
    }

    /** 提现申请 */
    @PostMapping("/withdraw/create")
    public R withdrawCreate(@Valid @RequestBody WithdrawReq req) {
        return fundService.withdrawCreate(req);
    }

    /** 提现记录 */
    @PostMapping("/withdraw/list")
    public R withdrawList(@RequestBody Map<String, Object> req) {
        String shopNum = req.get("shopNum") == null ? null : String.valueOf(req.get("shopNum"));
        int pageNum = req.get("pageNum") == null ? 1 : Integer.parseInt(String.valueOf(req.get("pageNum")));
        int pageSize = req.get("pageSize") == null ? 10 : Integer.parseInt(String.valueOf(req.get("pageSize")));
        return fundService.withdrawList(shopNum, pageNum, pageSize);
    }
}
