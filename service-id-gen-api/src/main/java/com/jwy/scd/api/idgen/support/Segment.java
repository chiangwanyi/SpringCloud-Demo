package com.jwy.scd.api.idgen.support;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 一个号段：闭区间 {@code [begin, end]} 加上一根内存游标。
 *
 * <p>号段模式的全部性能都来自这个类——发号实际就是 {@link #nextId()} 里对
 * {@link AtomicLong} 的一次自增（纳秒级），既不碰网络也不碰数据库。
 * 数据库只参与「把段批发出去」这一件事，频率是发号 QPS 的 1/step。
 *
 * <p><b>为什么 {@link #nextId()} 允许返回越界值</b>：如果在这里判断「是否耗尽」再决定返回什么，
 * 就得加锁或重试，快路径立刻变成两次原子操作。现在的约定是——
 * 取号方先取、再判断，越界了就走慢路径换段。代价是多消耗一个计数器位，收益是快路径只有一次 CAS。
 */
public final class Segment {

    /**
     * 预取触发比例：当前号段「<b>已下发</b>」到这个比例时，就该异步去取下一段了。
     *
     * <p>取 10% 的依据是美团 Leaf 设计文档的原话：「当前号段已下发 10% 时，如果下一个号段未更新，
     * 则另启一个更新线程去更新下一个号段」。
     *
     * <p>⚠️ <b>注意方向</b>：是「已下发 10% 就预取」，不是「剩余 10% 才预取」。
     * 两者差一个数量级的容错余量——前者的含义是把段内 <b>90%</b> 的时间预算全部留给那次 DB 往返，
     * 网络抖动、慢查询都能被这 90% 吸收掉；后者的余量只有 10%，一次稍慢的查询就会穿透到 P99 上。
     */
    public static final double PREFETCH_RATIO = 0.10D;

    /** 段内第一个号（含） */
    private final long begin;

    /** 段内最后一个号（含） */
    private final long end;

    /** 段长度。用 int 表示，超过 {@link Integer#MAX_VALUE} 的段在业务上不现实，做了饱和处理 */
    private final int step;

    /** 游标下发到这个位置就该触发预取：{@code begin + step * PREFETCH_RATIO} */
    private final long prefetchPoint;

    /** 下一个要发出去的号。发到 {@code end + 1} 即视为耗尽 */
    private final AtomicLong cursor;

    public Segment(long begin, long end) {
        if (begin > end) {
            throw new IllegalArgumentException("号段区间非法：begin=" + begin + " > end=" + end);
        }
        this.begin = begin;
        this.end = end;
        long length = end - begin + 1;
        this.step = (int) Math.min(Integer.MAX_VALUE, length);
        this.cursor = new AtomicLong(begin);
        // 至少 1，避免 step 很小时 (long)(len * 0.1) 取整成 0 导致「完全不预取」
        this.prefetchPoint = begin + Math.max(1L, (long) (length * PREFETCH_RATIO));
    }

    /**
     * 取下一个号。
     *
     * <p><b>返回值可能大于 {@link #getEnd()}</b>（即已越界），调用方必须自行判断后换段——
     * 这是刻意的设计，见类注释。
     */
    public long nextId() {
        return cursor.getAndIncrement();
    }

    /** 是否已耗尽（游标已越过 end） */
    public boolean isExhausted() {
        return cursor.get() > end;
    }

    /** 还剩多少个没发出去 */
    public long remaining() {
        long r = end - cursor.get() + 1;
        return r > 0 ? r : 0;
    }

    /** 本段已发出多少个 */
    public long consumed() {
        long c = cursor.get() - begin;
        return c > 0 ? c : 0;
    }

    /** 游标是否已经越过预取触发点 */
    public boolean shouldPrefetch(long justIssuedId) {
        return justIssuedId >= prefetchPoint;
    }

    public long getBegin() {
        return begin;
    }

    public long getEnd() {
        return end;
    }

    public int getStep() {
        return step;
    }

    public long getPrefetchPoint() {
        return prefetchPoint;
    }

    @Override
    public String toString() {
        return "[" + begin + ", " + end + "] 剩余 " + remaining() + "/" + step;
    }
}
