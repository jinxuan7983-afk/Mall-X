package com.mallx.mgr.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mallx.common.constant.MallConstant;
import com.mallx.common.exception.BizException;
import com.mallx.common.jwt.JwtUtil;
import com.mallx.common.result.R;
import com.mallx.common.util.OrderNoUtil;
import com.mallx.mgr.dto.LoginReq;
import com.mallx.mgr.dto.RegisterReq;
import com.mallx.mgr.entity.Mch;
import com.mallx.mgr.mapper.MchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 商户服务：注册入驻、登录（接口文档 4.1）。
 */
@Service
@RequiredArgsConstructor
public class MchService {

    private final MchMapper mchMapper;

    @Value("${mallx.jwt.secret:mallx-jwt-secret-2026}")
    private String jwtSecret;

    /** 商户注册：生成商户唯一编号，初始状态待审核 */
    public R register(RegisterReq req) {
        Long exists = mchMapper.selectCount(Wrappers.<Mch>lambdaQuery()
                .eq(Mch::getUsername, req.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException("商户登录账号已存在");
        }
        Mch mch = new Mch();
        mch.setShopNum(OrderNoUtil.shopNum());
        mch.setShopName(req.getShopName());
        mch.setShopPhone(req.getShopPhone());
        mch.setShopAddress(req.getShopAddress());
        mch.setUsername(req.getUsername());
        mch.setPassword(md5(req.getPassword()));
        mch.setShopState(MallConstant.MCH_WAIT_AUDIT);
        mchMapper.insert(mch);
        return R.ok("商户注册成功，等待审核").set("shopNum", mch.getShopNum());
    }

    /** 商户登录：返回登录令牌与商户状态 */
    public R login(LoginReq req) {
        Mch mch = mchMapper.selectOne(Wrappers.<Mch>lambdaQuery()
                .eq(Mch::getUsername, req.getUsername()));
        if (mch == null || !mch.getPassword().equals(md5(req.getPassword()))) {
            throw new BizException("账号或密码错误");
        }
        if (mch.getShopState() == MallConstant.MCH_DISABLED) {
            throw new BizException("商户已被禁用，请联系平台管理员");
        }
        String token = JwtUtil.create(jwtSecret, mch.getShopNum(), "MCH", mch.getShopName());
        R r = R.ok("登录成功");
        r.set("token", token);
        r.set("shopNum", mch.getShopNum());
        r.set("shopState", mch.getShopState());
        return r;
    }

    public String md5(String raw) {
        return DigestUtil.md5Hex(raw + MallConstant.PASSWORD_SALT);
    }
}
