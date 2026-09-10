package com.mallx.common.sign;

import cn.hutool.crypto.digest.DigestUtil;

import java.util.Map;
import java.util.TreeMap;

/**
 * 统一签名规则（MD5）：
 * 1. 取全部业务参数，剔除 sign、空值以及复杂类型（Map/Collection/数组不参与签名）；
 * 2. 按参数名 ASCII 升序拼接为 k1=v1&k2=v2...；
 * 3. 拼接 &key=密钥 后整体 MD5，得到 32 位小写签名。
 * 服务端以相同规则重算并比对，验签通过后方可执行业务逻辑，防止参数篡改。
 */
public class SignUtil {

    public static final String SIGN_FIELD = "sign";

    public static String sign(Map<String, String> params, String secret) {
        TreeMap<String, String> sorted = new TreeMap<>();
        params.forEach((k, v) -> {
            if (k == null || SIGN_FIELD.equals(k) || v == null || v.isEmpty()) {
                return;
            }
            sorted.put(k, v);
        });
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        sb.append("&key=").append(secret);
        return DigestUtil.md5Hex(sb.toString());
    }

    public static boolean verify(Map<String, String> params, String secret, String sign) {
        if (sign == null || sign.isEmpty()) {
            return false;
        }
        return sign(params, secret).equalsIgnoreCase(sign);
    }
}
