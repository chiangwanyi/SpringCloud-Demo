package com.jwy.scd.idgen.service;

import com.jwy.scd.api.idgen.dto.IdGenStatusDTO;
import com.jwy.scd.api.idgen.dto.SegmentDTO;
import com.jwy.scd.api.idgen.support.Segment;
import com.jwy.scd.api.idgen.support.SegmentBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 发号服务的主入口：维护「bizTag → 号段缓冲」的注册表。
 *
 * <h2>两个接口，两种形态</h2>
 *
 * <table border="1">
 *     <caption>服务端两条发号路径</caption>
 *     <tr><th>方法</th><th>是否持有状态</th><th>说明</th></tr>
 *     <tr>
 *         <td>{@link #fetchSegment}</td>
 *         <td><b>无状态</b></td>
 *         <td>直接向数据库取一段就返回。不缓存、不排队，所以 service-id-gen 可以随意横向扩容，
 *             数据库是唯一的冲突仲裁者。业务拿到段后在本地发号，零网络开销。</td>
 *     </tr>
 *     <tr>
 *         <td>{@link #nextId}</td>
 *         <td>有状态（本服务自己持段）</td>
 *         <td>走本进程的 {@link SegmentBuffer}。适合低频调用和调试；
 *             高频业务用它会把号段模式的吞吐优势让给网络往返。</td>
 *     </tr>
 * </table>
 *
 * <p>两条路径会从同一行 {@code leaf_alloc} 各自取段，区间互不重叠——
 * 因为不重叠这件事由 MySQL 的行锁保证，和「有几个取段方」无关。
 * 这也是号段模式能一边横向扩、一边保持唯一的根本原因。
 */
@Service
public class SegmentIdGen {

    private static final Logger log = LoggerFactory.getLogger(SegmentIdGen.class);

    private final LeafAllocator allocator;

    private final Executor prefetchExecutor;

    /** bizTag → 服务端持有的号段缓冲（仅 {@link #nextId} 路径使用） */
    private final Map<String, SegmentBuffer> buffers = new ConcurrentHashMap<>();

    /** 建 buffer 时防并发重复初始化。用独立锁对象而不是 synchronized(this)，避免和缓冲内部锁相互影响 */
    private final Object createLock = new Object();

    public SegmentIdGen(LeafAllocator allocator,
                        @Qualifier("idGenPrefetchExecutor") Executor prefetchExecutor) {
        this.allocator = allocator;
        this.prefetchExecutor = prefetchExecutor;
    }

    /**
     * 取单个 ID（服务端用自己的双 buffer 发号）。
     *
     * @throws com.jwy.scd.api.idgen.support.SegmentUnavailableException 段尽且取不到新段。
     *         由全局异常处理器转成 503 —— <b>不会</b>降级成本地自增（那是重号）。
     */
    public long nextId(String bizTag) {
        return bufferOf(bizTag).nextId();
    }

    /**
     * 领取一个新号段（号段下放，推荐路径）。<b>无状态</b>，不碰本进程的任何缓存。
     */
    public SegmentDTO fetchSegment(String bizTag) {
        Segment segment = allocator.nextSegment(bizTag);
        return SegmentDTO.of(bizTag, segment.getBegin(), segment.getEnd());
    }

    /**
     * 拿到（必要时创建）某个 bizTag 的服务端号段缓冲。
     *
     * <p>刻意不用 {@code buffers.computeIfAbsent(tag, ...)}：映射函数里要访问数据库，
     * 而 {@code computeIfAbsent} 会在持有哈希桶锁的情况下执行它，
     * 大 key 冲突或慢查询时可能拖住整个 map 的其它操作。这里用经典的
     * 「先无锁读，未命中再进锁二次确认」写法，语义一样但锁的范围完全可控。
     */
    public SegmentBuffer bufferOf(String bizTag) {
        SegmentBuffer buffer = buffers.get(bizTag);
        if (buffer != null) {
            return buffer;
        }
        synchronized (createLock) {
            buffer = buffers.get(bizTag);
            if (buffer == null) {
                Segment first = allocator.nextSegment(bizTag);
                buffer = new SegmentBuffer(bizTag, first, allocator::nextSegment, prefetchExecutor);
                buffers.put(bizTag, buffer);
                log.info("[id-gen] 初始化服务端号段缓冲：bizTag={}, 首段={}", bizTag, first);
            }
            return buffer;
        }
    }

    /**
     * 全部 bizTag 的号段状态快照，用于观察双 buffer 是否按预期工作
     * （对应 Leaf 的 {@code /cache} 监控页）。
     *
     * <p><b>只包含服务端持有缓冲的 bizTag</b>——纯粹走 {@code /api/id/segment} 的业务标签
     * 在这里看不到，因为服务端对它们本来就不持有任何状态。这是设计的必然结果，不是缺陷。
     */
    public List<IdGenStatusDTO> status() {
        return buffers.values().stream()
                .sorted(Comparator.comparing(SegmentBuffer::getBizTag))
                .map(this::toStatus)
                .toList();
    }

    /**
     * 启动预热：把 {@code leaf_alloc} 里配置的所有 bizTag 都先取一段放到内存。
     *
     * <p>不做预热的后果是「第一个请求承担一次取段开销」——冷启动时的首个调用会比常态慢几毫秒。
     * 对应 Leaf 的 {@code SegmentIDGenImpl#init()}。
     *
     * <p>刻意做成<b>尽力而为</b>：某个 tag 预热失败只记日志，不阻止服务启动。
     * 理由是发号服务往往先于业务启动，此刻数据库还没就绪是常见的；而启动后
     * 第一次真实请求会自然完成初始化，没有必要把这种情况升级成启动失败。
     */
    public void warmUpAll() {
        List<String> tags;
        try {
            tags = allocator.allBizTags();
        } catch (RuntimeException e) {
            log.warn("[id-gen] 启动预热跳过：读取 leaf_alloc 失败（服务仍会启动，首次请求时再初始化）", e);
            return;
        }
        if (tags == null || tags.isEmpty()) {
            log.warn("[id-gen] leaf_alloc 里没有任何 bizTag，发号前请先插入种子行");
            return;
        }
        for (String tag : tags) {
            try {
                SegmentBuffer buffer = bufferOf(tag);
                // 顺手把预备段也取回来，让双 buffer 从第一个请求起就是「两段在手」
                buffer.warmUp();
                log.info("[id-gen] 预热完成：bizTag={}, {}", tag, buffer);
            } catch (RuntimeException e) {
                log.warn("[id-gen] 预热失败（不影响启动，首次请求时会重试）：bizTag={}", tag, e);
            }
        }
    }

    private IdGenStatusDTO toStatus(SegmentBuffer buffer) {
        Segment current = buffer.getCurrent();
        IdGenStatusDTO dto = new IdGenStatusDTO();
        dto.setBizTag(buffer.getBizTag());
        dto.setCurrentBegin(current.getBegin());
        dto.setCurrentEnd(current.getEnd());
        dto.setCurrentRemaining(current.remaining());
        dto.setStep(current.getStep());
        dto.setNextReady(buffer.getNext() != null);
        dto.setPrefetching(buffer.isPrefetching());
        dto.setIssued(buffer.getIssued());
        return dto;
    }
}
