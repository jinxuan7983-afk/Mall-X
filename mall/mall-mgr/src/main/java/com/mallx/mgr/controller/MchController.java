package com.mallx.mgr.controller;

import com.mallx.common.result.R;
import com.mallx.mgr.dto.LoginReq;
import com.mallx.mgr.dto.RegisterReq;
import com.mallx.mgr.service.MchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商户管理模块接口（接口文档 4.1）。
 */
@RestController
@RequestMapping("/mch")
@RequiredArgsConstructor
public class MchController {

    private final MchService mchService;

    /** 4.1.1 商户注册接口 */
    @PostMapping("/register")
    public R register(@Valid @RequestBody RegisterReq req) {
        return mchService.register(req);
    }

    /** 4.1.2 商户登录接口 */
    @PostMapping("/login")
    public R login(@Valid @RequestBody LoginReq req) {
        return mchService.login(req);
    }
}
