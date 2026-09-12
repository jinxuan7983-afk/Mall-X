package com.mallx.mgr.support;

import com.mallx.common.exception.BizException;
import com.mallx.common.jwt.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 管理端敏感操作守卫：校验调用方登录令牌，确保仅 ADMIN 角色可执行删除等动作。
 * 令牌经请求头 X-Admin-Token 传递（管理端登录后携带），避免进入操作日志与接口签名参数。
 */
@Component
public class AdminGuard {

    @Value("${mallx.jwt.secret:mallx-jwt-secret-2026}")
    private String jwtSecret;

    /** 校验令牌合法性 + ADMIN 角色，通过后返回操作员账号；不满足则抛出业务异常 */
    public String requireAdmin(String operatorToken) {
        Map<String, Object> payload = JwtUtil.verify(jwtSecret, operatorToken);
        if (payload == null) {
            throw new BizException("登录凭证无效或已过期，请重新登录");
        }
        if (!"ADMIN".equals(String.valueOf(payload.get(JwtUtil.CLAIM_TYPE)))) {
            throw new BizException("仅 ADMIN 管理员可执行删除操作");
        }
        return String.valueOf(payload.get(JwtUtil.CLAIM_NUM));
    }
}
