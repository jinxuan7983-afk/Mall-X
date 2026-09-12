package com.mallx.common.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 单元测试：JWT 令牌签发与校验（权限凭证核心逻辑）。
 */
class JwtUtilTest {

    private static final String SECRET = "mallx-jwt-secret-2026";

    @Test
    @DisplayName("签发+校验:合法令牌返回完整载荷")
    void createAndVerify() {
        String token = JwtUtil.create(SECRET, "SH1000000001", "MCH", "星选数码旗舰店");
        Map<String, Object> payload = JwtUtil.verify(SECRET, token);
        assertNotNull(payload);
        assertEquals("SH1000000001", payload.get(JwtUtil.CLAIM_NUM));
        assertEquals("MCH", payload.get(JwtUtil.CLAIM_TYPE));
        assertEquals("星选数码旗舰店", payload.get(JwtUtil.CLAIM_NAME));
        assertNotNull(payload.get("exp"));
    }

    @Test
    @DisplayName("安全性:篡改令牌内容后校验失败")
    void tamperedTokenRejected() {
        String token = JwtUtil.create(SECRET, "U1000000001", "USER", "user1");
        String tampered = token.substring(0, token.length() - 3) + "abc";
        assertNull(JwtUtil.verify(SECRET, tampered), "签名被破坏必须校验失败");
    }

    @Test
    @DisplayName("安全性:使用其他密钥校验失败")
    void wrongSecretRejected() {
        String token = JwtUtil.create(SECRET, "U1", "USER", "u");
        assertNull(JwtUtil.verify("another-secret", token));
    }

    @Test
    @DisplayName("边界:空令牌/null 返回 null 而非抛异常")
    void blankToken() {
        assertNull(JwtUtil.verify(SECRET, ""));
        assertNull(JwtUtil.verify(SECRET, null));
    }

    @Test
    @DisplayName("边界:非法格式字符串不抛异常")
    void malformedToken() {
        assertNull(JwtUtil.verify(SECRET, "not-a-jwt"));
        assertNull(JwtUtil.verify(SECRET, "a.b.c"));
    }

    @Test
    @DisplayName("缺陷回归:iat/exp 必须为秒级时间戳(hutool validate 要求,毫秒会导致校验必然失败)")
    void timestampsInSeconds() {
        Map<String, Object> payload = JwtUtil.verify(SECRET, JwtUtil.create(SECRET, "U1", "USER", "u"));
        assertNotNull(payload);
        long exp = ((Number) payload.get("exp")).longValue();
        long iat = ((Number) payload.get("iat")).longValue();
        assertTrue(exp < 10_000_000_000L, "exp 应为秒级: " + exp);
        assertTrue(iat < 10_000_000_000L, "iat 应为秒级: " + iat);
        assertTrue(exp > iat, "exp 应晚于 iat");
    }

    @Test
    @DisplayName("有效期:签发令牌有效期约 7 天")
    void ttlAroundSevenDays() {
        Map<String, Object> payload = JwtUtil.verify(SECRET, JwtUtil.create(SECRET, "U1", "USER", "u"));
        long ttl = ((Number) payload.get("exp")).longValue() - ((Number) payload.get("iat")).longValue();
        assertEquals(7 * 24 * 3600L, ttl, "TTL 应为 7 天(秒)");
    }

    @Test
    @DisplayName("边界:不同身份类型可区分(USER/MCH/ADMIN 权限隔离基础)")
    void userTypePreserved() {
        String t1 = JwtUtil.create(SECRET, "A", "ADMIN", "admin");
        String t2 = JwtUtil.create(SECRET, "B", "USER", "user1");
        assertEquals("ADMIN", JwtUtil.verify(SECRET, t1).get(JwtUtil.CLAIM_TYPE));
        assertEquals("USER", JwtUtil.verify(SECRET, t2).get(JwtUtil.CLAIM_TYPE));
    }
}
