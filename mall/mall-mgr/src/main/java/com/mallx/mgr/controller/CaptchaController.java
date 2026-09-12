package com.mallx.mgr.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.mallx.common.result.R;
import com.mallx.mgr.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 图形验证码接口（GET 免签名）：注册页获取验证码图片。
 * 生成 captchaKey 与 4 位验证码，验证码存 Redis 5 分钟过期，一次性消费。
 */
@RestController
@RequestMapping("/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    /**
     * 测试环境回显验证码明文（echo-code=true 时响应附带 code 字段），
     * 供自动化测试脚本与联调使用；生产环境必须配置为 false。
     */
    @Value("${mallx.captcha.echo-code:false}")
    private boolean echoCode;

    @GetMapping("/image")
    public R image() {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 42, 4, 22);
        String code = captcha.getCode();
        String captchaKey = UUID.randomUUID().toString().replace("-", "");
        captchaService.save(captchaKey, code);

        R r = R.ok("验证码生成成功")
                .set("captchaKey", captchaKey)
                .set("image", captcha.getImageBase64Data());
        if (echoCode) {
            r.set("code", code);
        }
        return r;
    }
}
