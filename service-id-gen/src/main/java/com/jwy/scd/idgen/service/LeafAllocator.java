package com.jwy.scd.idgen.service;

import com.jwy.scd.api.idgen.support.Segment;
import com.jwy.scd.api.idgen.support.SegmentUnavailableException;
import com.jwy.scd.idgen.entity.LeafAlloc;
import com.jwy.scd.idgen.mapper.LeafAllocMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>号段批发器</b>——整个号段模式里唯一会写数据库的地方，也是正确性的唯一支点。
 *
 * <h2>为什么是「两条 SQL + 一个事务」</h2>
 *
 * <p>先把错误的写法摆出来，因为它看起来更自然、而且在单线程下完全正确：
 *
 * <pre>{@code
 *   // ❌ 天真写法：先查、再算、再写
 *   LeafAlloc a = mapper.selectByTag(tag);          // 读到 max_id = 1000
 *   long newMax = a.getMaxId() + a.getStep();       // 内存里算出 2000
 *   mapper.updateMaxId(tag, newMax);                // 写回 2000
 *   return new Segment(1001, 2000);
 * }</pre>
 *
 * <p>两个线程（或两个实例）同时执行时，都会读到 1000、都算出 2000、都拿到 {@code [1001, 2000]} ——
 * <b>这是真正的重号，不是无害的跳号</b>。而且它违反直觉地<b>不需要多实例就能复现</b>：
 * 单实例起 16 个线程压一下就会出现。
 *
 * <p>正确写法只有一条路——<b>让数据库自己做「读-改-写」这一个原子动作</b>：
 *
 * <pre>{@code
 *   BEGIN;
 *   UPDATE leaf_alloc SET max_id = max_id + step WHERE biz_tag = ?;  -- ① 行锁从这里开始
 *   SELECT max_id, step FROM leaf_alloc WHERE biz_tag = ?;           -- ② 读到的是自己写的值
 *   COMMIT;                                                          -- ③ 行锁到这里才释放
 * }</pre>
 *
 * <p>关键在于 ① 到 ③ 之间行锁一直没释放。并发的第二个事务会阻塞在 ①，
 * 等第一个提交后才继续，于是它读到的是 2000、拿到的是 {@code [2001, 3000]}。互不重叠。
 *
 * <h2>为什么不用 {@code LAST_INSERT_ID(max_id + step)}</h2>
 *
 * <p>那是一条 SQL 搞定的取巧写法，但它依赖 <b>连接级</b> 的 session 状态：
 * {@code LAST_INSERT_ID()} 只在「同一条连接」上能看到刚才那条 UPDATE 设的值。
 * 一旦连接池把连接换掉（或中间夹了别的语句），读到的就是别人的值。
 * 现在这种「靠行锁而非靠 session」的写法语义更直白，也不依赖任何隐式状态。
 *
 * <h2>为什么这个类必须是独立 Bean、方法必须是 public</h2>
 *
 * <p>{@code @Transactional} 是 <b>AOP 代理</b>生效的：代理包在 Bean 外面，
 * 只有「从外部调用」才会经过代理。如果这个类被注入到别处去调，或者把这段逻辑
 * 写成 Service 里的 private 方法再自调用，事务根本不会开启 ——
 * 表现就是「代码看起来完全正确，压测时却出重号」。
 * 这与本项目 {@code @SentinelResource} 必须放在独立 Bean 的 public 方法上是同一个道理。
 *
 * <p>（顺带一提：Leaf 官方实现用的是原生 MyBatis 手工 {@code openSession()} →
 * {@code update} → {@code selectOne} → {@code commit()}，在没有 Spring 的环境下这是唯一选择。
 * 本项目跑在 Spring 里，用声明式事务能得到同样的「同连接同事务」语义，而且少一层手工管理。）
 */
@Service
public class LeafAllocator {

    private static final Logger log = LoggerFactory.getLogger(LeafAllocator.class);

    private final LeafAllocMapper mapper;

    public LeafAllocator(LeafAllocMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 原子地批发一个新号段。
     *
     * @param bizTag 业务标识
     * @return 全新的、未被任何实例使用过的号段
     * @throws SegmentUnavailableException bizTag 不存在，或账本数据非法 —— 一律快速失败，
     *                                     绝不返回空段或降级成本地自增
     */
    @Transactional(rollbackFor = Exception.class)
    public Segment nextSegment(String bizTag) {
        if (bizTag == null || bizTag.isBlank()) {
            throw new SegmentUnavailableException("bizTag 不能为空");
        }

        // ① 原子推进 max_id。行锁从这里开始，直到本方法返回（事务提交）才释放。
        int affected = mapper.bumpMaxId(bizTag);
        if (affected == 0) {
            // 没有种子行。返回空段会让调用方静默发不出号，必须显式报错
            throw new SegmentUnavailableException(
                    "bizTag 未初始化，请先在 leaf_alloc 中插入种子行：bizTag=" + bizTag);
        }

        // ② 读回刚写进去的值。因为①的行锁还在，这里的 SELECT 不可能读到别人的写入
        LeafAlloc alloc = mapper.selectByTag(bizTag);
        if (alloc == null || alloc.getMaxId() == null || alloc.getStep() == null) {
            throw new SegmentUnavailableException("号段账本数据非法：bizTag=" + bizTag + ", row=" + alloc);
        }
        int step = alloc.getStep();
        if (step <= 0) {
            throw new SegmentUnavailableException("step 必须为正数：bizTag=" + bizTag + ", step=" + step);
        }

        long newMax = alloc.getMaxId();
        long begin = newMax - step + 1;

        if (log.isDebugEnabled()) {
            log.debug("[id-gen] 批发号段：bizTag={}, 新段=[{}, {}], 账本 max_id={}, step={}",
                    bizTag, begin, newMax, newMax, step);
        }
        return new Segment(begin, newMax);
    }

    /** 全部已配置的 bizTag（启动预热用） */
    public java.util.List<String> allBizTags() {
        return mapper.selectAllTags();
    }
}
