package com.jwy.scd.exception;

/**
 * 认证相关业务异常，由 {@link GlobalExceptionHandler} 统一转换为 401 响应。
 */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }
}
