package com.mallx.mgr.support;

import com.mallx.common.exception.BizException;
import com.mallx.common.jwt.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 商户端敏感操作守卫：校验调用方登录令牌并返回其商户编号。
 * 令牌经请求头 X-Mch-Token 传递（商户端登录后携带），避免进入操作日志与接口签名参数。
 */
@Component
public class MchGuard {

    @Value("${mallx.jwt.secret:mallx-jwt-secret-2026}")
    private String jwtSecret;

    /** 校验令牌合法性 + 商户角色，通过后返回商户编号；不满足则抛出业务异常 */
    public String requireMch(String operatorToken) {
        Map<String, Object> payload = JwtUtil.verify(jwtSecret, operatorToken);
        if (payload == null) {
            throw new BizException("登录凭证无效或已过期，请重新登录");
        }
        if (!"MCH".equals(String.valueOf(payload.get(JwtUtil.CLAIM_TYPE)))) {
            throw new BizException("仅商户可执行该操作");
        }
        return String.valueOf(payload.get(JwtUtil.CLAIM_NUM));
    }
}
