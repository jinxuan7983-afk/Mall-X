package com.mallx.common.sign;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 单元测试：统一签名工具（防篡改核心逻辑）。
 */
class SignUtilTest {

    private static final String SECRET = "mallx-pay-secret-2026";

    private Map<String, String> params(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("签名确定性:同参数同密钥结果一致(64位小写MD5)")
    void signIsDeterministic() {
        Map<String, String> p = params("shopNum", "SH1000000001", "payMoney", "199.00");
        String s1 = SignUtil.sign(p, SECRET);
        String s2 = SignUtil.sign(p, SECRET);
        assertEquals(s1, s2);
        assertTrue(s1.matches("[0-9a-f]{32}"), "应为32位小写MD5: " + s1);
    }

    @Test
    @DisplayName("验签:正确签名通过")
    void verifyOk() {
        Map<String, String> p = params("shopNum", "SH1000000001", "payMoney", "199.00");
        String sign = SignUtil.sign(p, SECRET);
        assertTrue(SignUtil.verify(p, SECRET, sign));
    }

    @Test
    @DisplayName("安全性:参数被篡改后验签失败")
    void verifyFailsWhenParamTampered() {
        Map<String, String> p = params("shopNum", "SH1000000001", "payMoney", "199.00");
        String sign = SignUtil.sign(p, SECRET);
        Map<String, String> tampered = params("shopNum", "SH1000000001", "payMoney", "0.01");
        assertFalse(SignUtil.verify(tampered, SECRET, sign), "金额被篡改必须验签失败");
    }

    @Test
    @DisplayName("安全性:密钥不匹配验签失败")
    void verifyFailsWhenSecretMismatch() {
        Map<String, String> p = params("a", "1");
        String sign = SignUtil.sign(p, SECRET);
        assertFalse(SignUtil.verify(p, "wrong-secret", sign));
    }

    @Test
    @DisplayName("边界:空签名/null 参数不通过")
    void verifyBlankSign() {
        Map<String, String> p = params("a", "1");
        assertFalse(SignUtil.verify(p, SECRET, ""));
        assertFalse(SignUtil.verify(p, SECRET, null));
    }

    @Test
    @DisplayName("边界:参数顺序不影响签名(按 key 升序拼接)")
    void signOrderInsensitive() {
        String s1 = SignUtil.sign(params("a", "1", "b", "2"), SECRET);
        String s2 = SignUtil.sign(params("b", "2", "a", "1"), SECRET);
        assertEquals(s1, s2);
    }

    @Test
    @DisplayName("边界:空值参数被剔除(不影响签名结果)")
    void blankValuesExcluded() {
        String s1 = SignUtil.sign(params("a", "1"), SECRET);
        String s2 = SignUtil.sign(params("a", "1", "b", ""), SECRET);
        assertEquals(s1, s2, "空值参数应被剔除");
    }

    @Test
    @DisplayName("边界:新增参数会改变签名(防追加)")
    void addedParamChangesSign() {
        String s1 = SignUtil.sign(params("a", "1"), SECRET);
        String s2 = SignUtil.sign(params("a", "1", "b", "2"), SECRET);
        assertNotEquals(s1, s2);
    }
}
