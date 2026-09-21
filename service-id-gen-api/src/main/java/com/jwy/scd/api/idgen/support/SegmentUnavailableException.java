package com.jwy.scd.api.idgen.support;

/**
 * 号段不可用：取不到段、或取回来的段非法。
 *
 * <p><b>为什么必须是「失败」而不是「降级」</b>：号段耗尽又取不到新段时，
 * 唯一错误的做法是退回本地自增（比如「内存里从 0 开始往上发」）——那会立刻产生跨实例重号，
 * 而且是静默的、要等唯一索引报错才暴露。正确行为是快速失败，
 * 让上层把请求拒掉（HTTP 503）。
 *
 * <p>这与本项目网关在 Redis 不可用时返回 503 而不是 401 是同一个判断逻辑：
 * <b>宁可拒绝服务，不可给出错误数据。</b>
 */
public class SegmentUnavailableException extends RuntimeException {

    public SegmentUnavailableException(String message) {
        super(message);
    }

    public SegmentUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
