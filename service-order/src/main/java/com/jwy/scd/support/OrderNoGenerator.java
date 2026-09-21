package com.jwy.scd.support;

import com.jwy.scd.api.idgen.support.SegmentUnavailableException;
import com.jwy.scd.exception.OrderException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务订单号生成器：{@code SO + yyyyMMdd + 号段 ID}，全局唯一。
 *
 * <p>例如 {@code SO20260921123456}。长度 ≤ 2 + 8 + 19 = 29 字符，
 * {@code biz_order.order_no} 是 {@code VARCHAR(40)}，留了余量。
 *
 * <h2>唯一性从哪来（职责分离）</h2>
 * 本类只负责<b>格式</b>，不负责唯一性。唯一性来自
 * {@link SegmentIdGenerator} → service-id-gen → MySQL {@code leaf_alloc} 那一行的行锁：
 * 每个实例领到互不重叠的号段，号段内靠内存自增，因此不可能重复。
 *
 * <p>日期段依旧只为人眼辨识与运维排查——<b>不承担任何唯一性职责</b>。
 * 也不必担心"跨天会不会重号"：号段是按消费速度批发的，不按天重置，
 * 所以同一天的订单号天然递增，跨天也不会有任何重叠。
 *
 * <h2>唯一性为什么可信</h2>
 * 号段模式的唯一性来自数据库行锁，与实例身份无关：多实例部署时不需要为任何一个实例
 * 分配「我是几号机」这类环境变量，起几个实例、在几台机器上起，代码和配置都完全一样。
 *
 * <h2>⚠️ 有意识付出的代价：订单号变成可枚举的</h2>
 * 号段 ID 连续递增，所以订单号可以按顺序猜出来。这带来两个真实风险：
 * <ol>
 *     <li><b>泄露业务量</b>：竞对在两天中午各下一单，用订单号相减就能大致算出平台一天的订单量。
 *         美团在设计文档里明确说这一点「不能忍受」——所以他们的做法是
 *         <b>对外标识用不可预测的算法生成，号段只用于内部主键</b>。</li>
 *     <li><b>放大 IDOR 风险</b>：本项目的 {@code GET /api/order/{id}} 目前缺归属校验
 *         （见项目记忆里的待办），而订单号又是 {@code /api/order/by-no/{orderNo}} 的入参。
 *         在号段模式下攻击者连「猜」都不用，直接 +1 即可。</li>
 * </ol>
 * 当前是学习项目、订单接口也还没对外发布，因此这里选择「按号段直接生成」这一最直白的形态，
 * 并把这个权衡显式记录在案。真要对外发布时有两条正路：
 * <ul>
 *     <li>对外标识做<b>可逆混淆</b>（Feistel / 乘加变换）——内部仍是纯数字，对外不可预测；</li>
 *     <li>订单号<b>不进入任何对外 API</b>，查询一律走带归属校验的接口。</li>
 * </ul>
 * 注意这两种做法都<b>替代不了归属校验</b>——混淆只防遍历，不防越权。
 *
 * <p>抽成独立 Bean 而不是把 format 写在 Service 里的原因同前：订单号策略
 * （号段 / Redis 日流水 / 数据库序列 / 可逆混淆）是可替换的决策点，独立出来便于单测与整体替换。
 */
@Component
public class OrderNoGenerator {

    private static final String PREFIX = "SO";

    /** 只取到「日」，精确时间不必再占位——号段本身递增，日期只为可读 */
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SegmentIdGenerator idGenerator;

    public OrderNoGenerator(SegmentIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    /**
     * 生成下一个订单号。
     *
     * <p>内部是「本地号段自增」，绝大多数调用不产生任何网络请求。
     *
     * @return 形如 {@code SO2026092112345}
     * @throws OrderException 503，发号服务或号段库不可用。
     *         这里刻意让下单失败，而不是退回某种本地兜底号——那会造成真实的重复订单号。
     */
    public String next() {
        long id;
        try {
            id = idGenerator.nextId();
        } catch (SegmentUnavailableException e) {
            // 发号是下单的前置依赖，拿不到号就必须拒绝服务。
            // 转成 OrderException 是为了让全局异常处理器返回明确的 503 + 可读原因，
            // 而不是把底层异常直接抛成 500。
            throw OrderException.serviceUnavailable("发号服务不可用，暂时无法受理下单：" + e.getMessage());
        }
        return PREFIX + LocalDate.now().format(DAY_FORMATTER) + id;
    }
}
