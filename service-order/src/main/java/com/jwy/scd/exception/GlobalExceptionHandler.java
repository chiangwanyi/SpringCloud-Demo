package com.jwy.scd.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理：把业务异常转成统一的 JSON 响应体，并沿用异常自带的 HTTP 状态码。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OrderException.class)
    public ResponseEntity<Map<String, Object>> handleOrderException(OrderException ex) {
        // 503（下游不可用）属于需要运维关注的异常，打 error；其余业务拒绝打 warn 即可
        if (ex.getStatus().is5xxServerError()) {
            log.error("订单业务异常: {}", ex.getMessage());
        } else {
            log.warn("订单业务异常: {}", ex.getMessage());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    /** 兜底：未预期的异常统一返回 500，避免把堆栈直接暴露给调用方 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("服务内部异常", ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", "服务内部错误：" + ex.getMessage());
        return ResponseEntity.internalServerError().body(body);
    }
}
