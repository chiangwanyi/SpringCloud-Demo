package com.jwy.scd.api.idgen.support;

/**
 * 号段来源。{@link SegmentBuffer} 只依赖这个函数式接口，不关心段是从哪来的——
 * 这正是「同一套双 buffer 状态机，服务端和客户端都能用」的关键：
 *
 * <ul>
 *     <li>service-id-gen 服务端：实现体走 {@code LeafAllocator}（MySQL 原子取段）；</li>
 *     <li>业务服务（service-order）客户端：实现体走 Feign 调 {@code /api/id/segment}。</li>
 * </ul>
 *
 * <p>不允许抛受检异常：底层要么是 JDBC（抛 {@code DataAccessException}），
 * 要么是 Feign（抛 {@code FeignException}），都是运行时异常。
 * 取段失败时应当抛出异常而不是返回 null —— 「拿不到段」和「拿到一个空段」是两回事，
 * 前者必须让调用方知道（并进入冷却重试），后者会静默发不出号。
 */
@FunctionalInterface
public interface SegmentSupplier {

    /**
     * 取一个新号段。
     *
     * @param bizTag 业务标识
     * @return 全新的、未被任何人使用过的号段；失败时抛运行时异常
     */
    Segment fetch(String bizTag);
}
