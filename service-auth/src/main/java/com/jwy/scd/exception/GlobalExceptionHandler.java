package com.jwy.scd.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;

/**
 * 全局异常处理：统一输出 RFC 7807 ProblemDetail。
 *
 * <p>与 service-system / service-order 保持一致的结构（三个微服务统一错误契约）：
 * <pre>
 * { "type": "about:blank", "title": "Unauthorized", "status": 401,
 *   "detail": "用户名或密码错误", "instance": "/api/auth/login" }
 * </pre>
 * 认证失败（AuthException）统一映射为 401，不再用自定义 Map 响应体。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 认证异常：凭据错误 / 令牌无效 / 下游认证服务不可用 → 401 */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ProblemDetail> handleAuthException(AuthException ex, HttpServletRequest request) {
        log.warn("认证异常: {}", ex.getMessage());
        return toProblem(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    /** 400：请求体 JSON 解析失败 / 路径参数类型不匹配 */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(Exception ex, HttpServletRequest request) {
        log.warn("请求参数不合法: {}", ex.getMessage());
        return toProblem(HttpStatus.BAD_REQUEST, "请求参数不合法：" + ex.getMessage(), request);
    }

    /** 404：请求了不存在的接口 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return toProblem(HttpStatus.NOT_FOUND, "接口不存在：" + ex.getResourcePath(), request);
    }

    /** 兜底：未预期的异常统一返回 500，避免把堆栈直接暴露给调用方 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("服务内部异常", ex);
        return toProblem(HttpStatus.INTERNAL_SERVER_ERROR, "服务内部错误：" + ex.getMessage(), request);
    }

    /** 组装 RFC 7807 ProblemDetail：title 自动取状态码标准短语，detail 为业务信息，instance 为请求路径 */
    private ResponseEntity<ProblemDetail> toProblem(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).body(problemDetail);
    }
}
