package com.mallx.common.exception;

import lombok.Getter;

/**
 * 业务异常：message 将通过统一异常处理器返回给调用方。
 */
@Getter
public class BizException extends RuntimeException {

    private final String code;

    public BizException(String message) {
        super(message);
        this.code = "500";
    }

    public BizException(String code, String message) {
        super(message);
        this.code = code;
    }
}
