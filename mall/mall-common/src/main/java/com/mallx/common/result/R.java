package com.mallx.common.result;

import java.util.LinkedHashMap;

/**
 * 统一响应体：包含状态码 id、提示信息 message 及业务数据字段（平铺在顶层，与接口文档示例一致）。
 * id = "0" 表示成功，非 0 表示失败。
 */
public class R extends LinkedHashMap<String, Object> {

    public static final String SUCCESS = "0";
    public static final String FAIL = "500";
    public static final String UNAUTHORIZED = "401";
    public static final String BAD_REQUEST = "400";

    public static R ok(String message) {
        R r = new R();
        r.put("id", SUCCESS);
        r.put("message", message);
        return r;
    }

    public static R fail(String message) {
        return fail(FAIL, message);
    }

    public static R fail(String code, String message) {
        R r = new R();
        r.put("id", code);
        r.put("message", message);
        return r;
    }

    public R set(String key, Object value) {
        put(key, value);
        return this;
    }
}
