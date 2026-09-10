package com.mallx.common.sign;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一签名算法单元测试。
 */
class SignUtilTest {

    private static final String SECRET = "mallx-pay-secret-2026";

    @Test
    void signShouldIgnoreSignFieldAndEmptyValue() {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("shopNum", "SH1000000001");
        a.put("payMoney", "199.00");
        a.put("sign", "anything");

        Map<String, String> b = new LinkedHashMap<>();
        b.put("payMoney", "199.00");
        b.put("shopNum", "SH1000000001");

        assertEquals(SignUtil.sign(a, SECRET), SignUtil.sign(b, SECRET), "sign字段不参与签名且与参数顺序无关");
    }

    @Test
    void signShouldBeDeterministicMd5Lowercase() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("shopNum", "SH1000000001");
        params.put("payMoney", "199.00");
        String sign = SignUtil.sign(params, SECRET);

        assertEquals(32, sign.length(), "签名为32位MD5");
        assertEquals(sign.toLowerCase(), sign, "签名为小写");
        // 拼接串: payMoney=199.00&shopNum=SH1000000001&key=mallx-pay-secret-2026
        String expected = cn.hutool.crypto.digest.DigestUtil.md5Hex("payMoney=199.00&shopNum=SH1000000001&key=" + SECRET);
        assertEquals(expected, sign);
    }

    @Test
    void verifyShouldRejectTamperedParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("payMoney", "199.00");
        String sign = SignUtil.sign(params, SECRET);
        assertTrue(SignUtil.verify(params, SECRET, sign));

        params.put("payMoney", "1.00");
        assertFalse(SignUtil.verify(params, SECRET, sign), "参数被篡改后验签必须失败");
        assertFalse(SignUtil.verify(params, SECRET, null), "缺少签名验签失败");
    }
}
