package com.mallx.mgr.controller;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mallx.common.constant.MallConstant;
import com.mallx.common.exception.BizException;
import com.mallx.common.jwt.JwtUtil;
import com.mallx.common.result.R;
import com.mallx.common.util.OrderNoUtil;
import com.mallx.mgr.dto.LoginReq;
import com.mallx.mgr.dto.UserRegisterReq;
import com.mallx.mgr.entity.User;
import com.mallx.mgr.mapper.UserMapper;
import com.mallx.mgr.service.CaptchaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台用户接口：注册 / 登录（普通用户购物侧）。
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final CaptchaService captchaService;

    @Value("${mallx.jwt.secret:mallx-jwt-secret-2026}")
    private String jwtSecret;

    @PostMapping("/register")
    public R register(@Valid @RequestBody UserRegisterReq req) {
        // 图形验证码：一次性校验（Redis 5 分钟过期，校验后即删除）
        captchaService.verifyAndConsume(req.getCaptchaKey(), req.getCaptchaCode());
        Long exists = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getUsername, req.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException("用户账号已存在");
        }
        User user = new User();
        user.setUserNum(OrderNoUtil.userNum());
        user.setUsername(req.getUsername());
        user.setPassword(DigestUtil.md5Hex(req.getPassword() + MallConstant.PASSWORD_SALT));
        user.setNickname(req.getUsername());
        try {
            userMapper.insert(user);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发兜底：uk_user_username 唯一索引冲突时返回友好提示而非 500
            throw new BizException("用户账号已存在");
        }
        return R.ok("用户注册成功").set("userNum", user.getUserNum());
    }

    @PostMapping("/login")
    public R login(@Valid @RequestBody LoginReq req) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, req.getUsername()));
        if (user == null || !user.getPassword().equals(DigestUtil.md5Hex(req.getPassword() + MallConstant.PASSWORD_SALT))) {
            throw new BizException("账号或密码错误");
        }
        String token = JwtUtil.create(jwtSecret, user.getUserNum(), "USER", user.getNickname());
        return R.ok("登录成功")
                .set("token", token)
                .set("userNum", user.getUserNum());
    }
}
