package com.mallx.common.jwt;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.JWTPayload;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 登录令牌工具（基于 hutool-jwt，HS256）。
 */
public class JwtUtil {

    public static final String CLAIM_NUM = "num";
    public static final String CLAIM_TYPE = "userType";
    public static final String CLAIM_NAME = "name";

    private static final long TTL_MILLIS = 7L * 24 * 3600 * 1000;

    public static String create(String secret, String num, String userType, String name) {
        long now = System.currentTimeMillis();
        Map<String, Object> payload = new HashMap<>();
        payload.put(CLAIM_NUM, num);
        payload.put(CLAIM_TYPE, userType);
        payload.put(CLAIM_NAME, name);
        payload.put(JWTPayload.ISSUED_AT, now);
        payload.put(JWTPayload.EXPIRES_AT, now + TTL_MILLIS);
        return JWTUtil.createToken(payload, secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 校验令牌合法性（签名 + 过期时间），合法返回 payload，非法返回 null */
    public static Map<String, Object> verify(String secret, String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            JWT jwt = JWTUtil.parseToken(token);
            if (!jwt.setKey(secret.getBytes(StandardCharsets.UTF_8)).verify()) {
                return null;
            }
            if (!jwt.validate(0)) {
                return null;
            }
            return jwt.getPayloads();
        } catch (Exception e) {
            return null;
        }
    }
}
