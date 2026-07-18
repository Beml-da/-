package com.example.demo.common;

/**
 * 业务异常：用于业务层主动抛出，由 GlobalExceptionHandler 统一转换为 Result。
 * code 使用业务错误码（如 4001 表示"商品不存在"，4291 表示"限流触发"）。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = 400;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}