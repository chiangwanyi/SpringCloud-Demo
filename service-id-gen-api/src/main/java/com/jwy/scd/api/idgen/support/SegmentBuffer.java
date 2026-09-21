package com.jwy.scd.api.idgen.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * <b>号段消费器（双 buffer）</b>——Leaf-segment 里最值得抄的那部分。
 *
 * <p>它持有两个号段：{@link #current}（正在发）和 {@link #next}（预备）。
 * 目标是让「取下一段」这次数据库/网络往返<b>不出现在发号请求的关键路径上</b>。
 *
 * <h3>两条路径</h3>
 * <ol>
 *   <li><b>快路径</b>：直接 {@code cursor++}，一次 CAS，不加锁、不判断耗尽分支以外的任何东西。
 *       绝大多数请求走这里。</li>
 *   <li><b>慢路径</b>：当前段耗尽时换指针。如果 {@link #next} 已就绪，
 *       换指针是纯粹的内存赋值（业务线程依然不等 DB）；只有 {@code next} 没准备好时，
 *       才会在这里同步取段——<b>这一行就是 TP999 尖刺的唯一来源</b>，
 *       也是双 buffer 存在的全部理由。</li>
 * </ol>
 *
 * <h3>预取时机</h3>
 * 官方口径：当前号段<b>已下发 10%</b> 就异步加载下一段（见 {@link Segment#PREFETCH_RATIO}）。
 * 这样段内剩下的 90% 时间预算全部用来兜那次往返。
 *
 * <h3>两个必须自己实现、否则会静默出问题的细节</h3>
 * <ol>
 *   <li><b>预取必须「单飞」</b>。如果只写 {@code if (next == null) prefetch()}，
 *       2000 个并发线程会同时判定成立、一起打向发号服务——自己 DDoS 自己。
 *       这里用 {@link AtomicBoolean#compareAndSet} 保证同一时刻只有一个预取任务在跑。</li>
 *   <li><b>预取失败必须能重试</b>。失败后若不复位单飞标志，一次网络抖动就会把预取永久锁死，
 *       下一段永远不来，直到当前段耗尽才在慢路径上暴露成同步取段。
 *       这里在 {@code finally} 里复位，并加一个冷却窗口，避免 DB 长时间不可用时
 *       被每一个发号请求反复重试打爆。</li>
 * </ol>
 *
 * <h3>降级红线</h3>
 * 段尽且取不到新段时，{@link #nextId()} 抛出 {@link SegmentUnavailableException}。
 * <b>绝不能退回本地自增</b>——那是跨实例重号，且静默。
 *
 * <p>本类刻意不依赖 Spring（只用 JDK + slf4j），因为它同时被服务端和业务客户端复用，
 * 而 api 契约模块不该拖进框架依赖。异步预取用的线程池由调用方注入。
 */
public class SegmentBuffer {

    private static final Logger log = LoggerFactory.getLogger(SegmentBuffer.class);

    /**
     * 预取失败后的冷却窗口。DB 长时间不可用期间，当前段每发一个号都会重新判断「该不该预取」，
     * 没有冷却就会变成「每个请求都往 DB 怼一次」。200ms 足够让一次瞬时抖动过去，
     * 又不会让重试间隔长得离谱。
     */
    private static final long PREFETCH_RETRY_COOLDOWN_MS = 200L;

    /** 换段循环的保险丝：正常情况下 1~2 轮就出去，连续失败说明底层取号源已经坏了 */
    private static final int MAX_SWITCH_ATTEMPTS = 8;

    private final String bizTag;

    /** 号段来源（服务端=数据库，客户端=Feign） */
    private final SegmentSupplier supplier;

    /** 异步预取用的线程池。用 {@link Executor} 而不是具体线程池类型，是为了不绑定任何线程池实现 */
    private final Executor prefetchExecutor;

    /** 正在发号的段 */
    private volatile Segment current;

    /** 预备段。为 null 表示「还没预取」或「已经切换掉了」 */
    private volatile Segment next;

    /** 单飞标志：同一个 buffer 同时只允许一个预取任务在飞 */
    private final AtomicBoolean prefetching = new AtomicBoolean(false);

    /** 上次预取失败的时间戳，用于冷却。0 表示从未失败过 */
    private volatile long lastPrefetchFailAt = 0L;

    /** 累计发出多少号。LongAdder 比 AtomicLong 在高并发下争用更小（分槽累加） */
    private final LongAdder issued = new LongAdder();

    public SegmentBuffer(String bizTag, Segment first, SegmentSupplier supplier, Executor prefetchExecutor) {
        if (first == null) {
            throw new IllegalArgumentException("初始号段不能为空：bizTag=" + bizTag);
        }
        this.bizTag = bizTag;
        this.supplier = supplier;
        this.prefetchExecutor = prefetchExecutor;
        this.current = first;
    }

    /**
     * 取下一个号。发号的主入口，线程安全。
     *
     * @throws SegmentUnavailableException 段尽且取不到新段（调用方应转成 503，而不是降级）
     */
    public long nextId() {
        for (int attempt = 0; attempt < MAX_SWITCH_ATTEMPTS; attempt++) {
            Segment seg = this.current;
            long id = seg.nextId();

            // ---------- 快路径 ----------
            if (id <= seg.getEnd()) {
                issued.increment();
                // 已下发到预取点且预备段还没到 → 异步补货，不阻塞本次发号
                if (this.next == null && seg.shouldPrefetch(id)) {
                    tryPrefetch();
                }
                return id;
            }

            // ---------- 慢路径：当前段耗尽，换段 ----------
            switchSegment(seg);
        }
        throw new SegmentUnavailableException("号段连续切换 " + MAX_SWITCH_ATTEMPTS + " 次仍不可用：bizTag=" + bizTag);
    }

    /**
     * 换段。若预备段已就绪则只做指针交换；否则同步取段（尖刺就在这里）。
     *
     * <p>用 {@code synchronized} 是刻意的：段耗尽的那一刻，多个业务线程会同时进来，
     * 必须让它们整齐地等在同一个锁上，而不是各自去取一次段（那会白白浪费掉好几段）。
     * Leaf 的做法也是阻塞等待，代价是耗尽瞬间的 RT 被拉长——这就是它的 TP999 缺点。
     */
    private void switchSegment(Segment exhausted) {
        synchronized (this) {
            if (this.current != exhausted) {
                // 别的线程已经切好了，直接重试即可（正常的高并发路径）
                return;
            }
            Segment prepared = this.next;
            if (prepared == null) {
                long t0 = System.nanoTime();
                prepared = fetchOrThrow();
                long costMs = (System.nanoTime() - t0) / 1_000_000L;
                log.warn("[id-gen] 号段耗尽且预备段未就绪，已同步取段（本次发号多花 {} ms）：bizTag={}, 新段={}",
                        costMs, bizTag, prepared);
            }
            this.current = prepared;
            this.next = null;
        }
    }

    /**
     * 异步预取下一段。<b>单飞 + 冷却 + 失败复位</b>，见类注释。
     */
    private void tryPrefetch() {
        if (this.next != null) {
            return;
        }
        if (System.currentTimeMillis() - lastPrefetchFailAt < PREFETCH_RETRY_COOLDOWN_MS) {
            return;
        }
        if (!prefetching.compareAndSet(false, true)) {
            return;
        }
        try {
            prefetchExecutor.execute(() -> {
                try {
                    Segment s = fetchOrThrow();
                    this.next = s;
                    log.debug("[id-gen] 预取成功：bizTag={}, next={}", bizTag, s);
                } catch (RuntimeException e) {
                    lastPrefetchFailAt = System.currentTimeMillis();
                    log.warn("[id-gen] 预取下一号段失败，已进入 {}ms 冷却（当前号段仍可继续发号，"
                                    + "耗尽后会退回同步取段）：bizTag={}",
                            PREFETCH_RETRY_COOLDOWN_MS, bizTag, e);
                } finally {
                    // ★ 必须复位：否则一次失败就把预取永久锁死
                    prefetching.set(false);
                }
            });
        } catch (RuntimeException rejected) {
            // 线程池拒绝（队列满 / 已关闭）。同样必须复位，否则预取再也不会被触发
            prefetching.set(false);
            log.warn("[id-gen] 预取任务被线程池拒绝：bizTag={}", bizTag, rejected);
        }
    }

    private Segment fetchOrThrow() {
        try {
            Segment s = supplier.fetch(bizTag);
            if (s == null || s.getBegin() > s.getEnd()) {
                throw new SegmentUnavailableException("取到的号段非法：bizTag=" + bizTag + ", segment=" + s);
            }
            return s;
        } catch (SegmentUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SegmentUnavailableException("取号段失败：bizTag=" + bizTag, e);
        }
    }

    /** 触发一次预取（供服务启动时预热，避免第一个请求承担取段开销） */
    public void warmUp() {
        tryPrefetch();
    }

    public String getBizTag() {
        return bizTag;
    }

    public Segment getCurrent() {
        return current;
    }

    public Segment getNext() {
        return next;
    }

    public boolean isPrefetching() {
        return prefetching.get();
    }

    public long getIssued() {
        return issued.sum();
    }

    @Override
    public String toString() {
        return "SegmentBuffer{bizTag=" + bizTag
                + ", current=" + current
                + ", next=" + next
                + ", issued=" + issued.sum() + "}";
    }
}
