package com.jwy.scd.idgen.exception;

import com.jwy.scd.api.idgen.support.SegmentUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发号服务异常处理。
 *
 * <p><b>刻意不写 catch-all 的 {@code @ExceptionHandler(Exception.class)}</b>：
 * 那会把 Spring MVC 自己的异常（404 找不到处理器、405 方法不允许、415 媒体类型不支持）
 * 一并吞掉、统一变成 500，反而让调用方拿到错误的诊断信息。
 * 这里只处理「发号确实不可用」这一类。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 号段不可用 → <b>503</b>。
     *
     * <p>返回 503 而不是 200 带错误码，是因为调用方（业务服务）必须能明确区分
     * 「这个号是有效的」和「我没拿到号」——号段模式下只有这两态，没有中间态。
     * <b>绝不能返回一个本地自增的兜底号</b>，那会造成跨实例重号且静默。
     */
    @ExceptionHandler(SegmentUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> handleSegmentUnavailable(SegmentUnavailableException e) {
        log.error("[id-gen] 号段不可用，拒绝服务：{}", e.getMessage(), e);
        return body(503, e.getMessage());
    }

    /** 数据库不可用（连接失败 / 超时 / SQL 异常）→ 同样是 503 */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> handleDataAccess(DataAccessException e) {
        log.error("[id-gen] 号段库访问失败，拒绝服务：{}", e.getMessage(), e);
        return body(503, "号段库暂时不可用：" + e.getMessage());
    }

    private Map<String, Object> body(int code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().toString());
        return body;
    }
}
