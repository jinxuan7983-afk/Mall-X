package com.mallx.mgr.controller;

import com.mallx.common.result.R;
import com.mallx.mgr.dto.StatisMchReq;
import com.mallx.mgr.service.StatisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据统计接口（接口文档 4.6）。
 */
@RestController
@RequiredArgsConstructor
public class StatisController {

    private final StatisService statisService;

    /** 4.6.1 平台交易统计接口 */
    @PostMapping("/statis/platform")
    public R platform() {
        return statisService.platform();
    }

    /** 4.6.2 商户交易统计接口 */
    @PostMapping("/statis/mch")
    public R mch(@Valid @RequestBody StatisMchReq req) {
        return statisService.mch(req);
    }
}
