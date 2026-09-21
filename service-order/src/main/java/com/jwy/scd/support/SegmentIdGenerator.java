package com.jwy.scd.support;

import com.jwy.scd.api.idgen.IdGenApi;
import com.jwy.scd.api.idgen.dto.SegmentDTO;
import com.jwy.scd.api.idgen.support.Segment;
import com.jwy.scd.api.idgen.support.SegmentBuffer;
import com.jwy.scd.api.idgen.support.SegmentUnavailableException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <b>号段发号器（业务侧 / 消费端）</b>——本服务产生 ID 的唯一入口。
 *
 * <h2>它做的事</h2>
 * 启动时（或首次发号时）向 service-id-gen 领一段连续 ID，之后所有发号都在本地内存自增完成，
 * <b>一次网络请求都不需要</b>；等这段快用完时再异步去领下一段。
 *
 * <p>「双 buffer + 单飞预取 + 失败冷却」这套状态机写在
 * {@link SegmentBuffer}（在 service-id-gen-api 模块里），本类只负责三件事：
 * <ol>
 *     <li>把 {@link IdGenApi} 包装成 {@link SegmentBuffer} 需要的取段函数（{@link #fetchSegment}）；</li>
 *     <li>提供预取用的线程池；</li>
 *     <li>管理这个 buffer 的生命周期（懒初始化 + 关闭）。</li>
 * </ol>
 *
 * <h2>为什么号段消费组件放在 api 模块里，服务端和客户端共用一套</h2>
 * 号段模式的正确性完全由「持有号段的那一侧」的双 buffer 决定，而
 * 「从谁那里取段」这件事在形态上是可以互换的：
 * <ul>
 *     <li>service-id-gen 服务端用 {@code LeafAllocator}（直连 MySQL）当取段函数；</li>
 *     <li>本服务用 {@link IdGenApi}（HTTP）当取段函数。</li>
 * </ul>
 * 两者的状态机完全相同，没有理由写两遍——这也是 Leaf 把 {@code SegmentBuffer} 放进
 * {@code leaf-core} 让业务服务直接依赖的原因。放在 api 模块的代价是契约模块多了
 * 两个运行时类，收益是「一套状态机、一份测试」。
 *
 * <h2>唯一性为什么可信</h2>
 * 号段模式的唯一性来自 MySQL 单行 {@code UPDATE} 的行锁：并发实例抢到的是互不重叠的区间，
 * 号段内又靠内存自增，因此不可能重复。这一点<b>与实例身份无关</b>——
 * 起 1 个实例和起 100 个实例，代码、配置、部署方式完全一样，
 * 不需要给任何实例分配「我是几号机」这类环境变量。
 */
@Component
public class SegmentIdGenerator {

    private static final Logger log = LoggerFactory.getLogger(SegmentIdGenerator.class);

    private final IdGenApi idGenApi;

    /** 本服务在号段账本里的业务标签，必须与 leaf_alloc 中的种子行一致 */
    private final String bizTag;

    private final ExecutorService prefetchExecutor;

    private final Object createLock = new Object();

    /** 本地号段缓冲。null 表示还没成功领到第一个段（发号服务不可用时会一直是 null） */
    private volatile SegmentBuffer buffer;

    public SegmentIdGenerator(IdGenApi idGenApi,
                              @Value("${app.id-gen.biz-tag:order}") String bizTag) {
        this.idGenApi = idGenApi;
        this.bizTag = bizTag;

        // 单线程：本服务只有一个 bizTag，预取是低频操作，一条线程足够。
        // 守护线程 + 无界队列（newSingleThreadExecutor 自带），因此 execute() 不会抛拒绝异常。
        AtomicInteger seq = new AtomicInteger();
        this.prefetchExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread t = new Thread(runnable, "id-gen-prefetch-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });

        // 打印实际生效的值：配置写错了（比如 bizTag 拼错、配置没加载）在启动日志里就能看见，
        // 而不是等到第一次下单才报「bizTag 未初始化」。
        log.info("[id-gen-client] 号段发号器已装配：bizTag={}", bizTag);
    }

    /**
     * 取下一个 ID（业务唯一入口）。
     *
     * <p>线程安全，且<b>绝大多数调用只做一次内存自增</b>。
     *
     * @throws SegmentUnavailableException 领不到号段（发号服务或号段库不可用）。
     *         调用方必须让它失败，<b>绝不能</b>退回本地自增——那是跨实例重号。
     */
    public long nextId() {
        SegmentBuffer b = this.buffer;
        if (b == null) {
            b = initBuffer();
        }
        return b.nextId();
    }

    /**
     * 启动预热：先把首段领回来。
     *
     * <p>尽力而为——不在启动阶段就把「发号服务是否可用」升级成致命错误。
     * 理由是 service-id-gen 可能比本服务晚启动，而本服务在没有发号请求时并不需要号段。
     * 预热失败后第一次 {@link #nextId()} 会再试一次。
     */
    public void warmUp() {
        try {
            SegmentBuffer b = initBuffer();
            // 顺手把预备段也领回来，让第一个请求起就是「两段在手」
            b.warmUp();
            log.info("[id-gen-client] 号段预热完成：bizTag={}, {}", bizTag, describe());
        } catch (RuntimeException e) {
            log.warn("[id-gen-client] 号段预热失败（服务照常启动，首次发号时会重试）：bizTag={}", bizTag, e);
        }
    }

    /** 本地缓冲的运行状态，便于排查「号段为什么没换」之类的问题 */
    public String describe() {
        SegmentBuffer b = this.buffer;
        return b == null ? "未初始化（尚未领到号段）" : b.toString();
    }

    public String getBizTag() {
        return bizTag;
    }

    private SegmentBuffer initBuffer() {
        synchronized (createLock) {
            if (buffer == null) {
                Segment first;
                try {
                    first = fetchSegment(bizTag);
                } catch (SegmentUnavailableException e) {
                    throw e;
                } catch (RuntimeException e) {
                    // Feign 异常 / 序列化异常等统一收敛成「号段不可用」，
                    // 让上层只需处理一种异常语义
                    throw new SegmentUnavailableException("向发号服务领取号段失败：bizTag=" + bizTag, e);
                }
                buffer = new SegmentBuffer(bizTag, first, this::fetchSegment, prefetchExecutor);
                log.info("[id-gen-client] 初始化本地号段缓冲：bizTag={}, 首段={}", bizTag, first);
            }
            return buffer;
        }
    }

    /**
     * {@link SegmentBuffer} 的取段函数：走 Feign 向 service-id-gen 领一个新段。
     *
     * <p>注意这是 {@code /api/id/segment}（领一段），不是 {@code /api/id/next}（领一个）。
     * 用后者的话，每次发号都要一次 HTTP 往返，号段模式省下的数据库压力会被网络开销吃掉。
     */
    private Segment fetchSegment(String tag) {
        SegmentDTO dto = idGenApi.getSegment(tag);
        if (dto == null) {
            throw new SegmentUnavailableException("发号服务返回了空号段：bizTag=" + tag);
        }
        return dto.toSegment();
    }

    @PreDestroy
    public void shutdown() {
        // 线程都是守护线程，不关也不会拖住 JVM 退出；显式关掉是为了让「服务停止」这件事干净
        prefetchExecutor.shutdownNow();
    }
}
