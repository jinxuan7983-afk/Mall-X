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
    private final CaptchaService captchaService;

    @Value("${mallx.jwt.secret:mallx-jwt-secret-2026}")
    private String jwtSecret;

    /** 商户注册：图形验证码校验 → 生成商户唯一编号，初始状态待审核 */
    public R register(RegisterReq req) {
        // 图形验证码：一次性校验（Redis 5 分钟过期，校验后即删除）
        captchaService.verifyAndConsume(req.getCaptchaKey(), req.getCaptchaCode());
        Long exists = mchMapper.selectCount(Wrappers.<Mch>lambdaQuery()
                .eq(Mch::getUsername, req.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException("商户登录账号已存在");
        }
        Mch mch = new Mch();
        mch.setShopName(req.getShopName());
        mch.setShopPhone(req.getShopPhone());
        mch.setShopAddress(req.getShopAddress());
        mch.setUsername(req.getUsername());
        mch.setPassword(md5(req.getPassword()));
        mch.setShopState(MallConstant.MCH_WAIT_AUDIT);
        // 短编号碰撞概率极低：唯一索引兜底，冲突时重新生成编号重试
        for (int attempt = 0; attempt < 3; attempt++) {
            mch.setShopNum(OrderNoUtil.shopNum());
            try {
                mchMapper.insert(mch);
                return R.ok("商户注册成功，等待审核").set("shopNum", mch.getShopNum());
            } catch (org.springframework.dao.DuplicateKeyException e) {
                String detail = String.valueOf(e.getMessage());
                if (detail.contains("uk_mch_username")) {
                    // 并发兜底：uk_mch_username 唯一索引冲突时返回友好提示而非 500
                    throw new BizException("商户登录账号已存在");
                }
                // uk_mch_shop_num 冲突：重新生成编号后重试
            }
        }
        throw new BizException("系统繁忙，请稍后重试");
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
