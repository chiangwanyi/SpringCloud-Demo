package com.jwy.scd.exception;

import org.springframework.http.HttpStatus;

/**
 * 订单业务异常，由 {@link GlobalExceptionHandler} 统一转换 HTTP 状态码与响应体。
 *
 * <p>相比 service-auth 里只有一个 401 的场景，订单服务需要区分多种失败语义，
 * 因此这里把 HttpStatus 一并携带出来，而不是在注解上写死。
 */
public class OrderException extends RuntimeException {

    private final HttpStatus status;

    public OrderException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** 400：入参或业务规则不满足（如库存不足、用户被禁用） */
    public static OrderException badRequest(String message) {
        return new OrderException(HttpStatus.BAD_REQUEST, message);
    }

    /** 404：目标资源不存在 */
    public static OrderException notFound(String message) {
        return new OrderException(HttpStatus.NOT_FOUND, message);
    }

    /** 503：依赖的下游服务不可用（跨服务调用失败） */
    public static OrderException serviceUnavailable(String message) {
        return new OrderException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
