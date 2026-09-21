package com.jwy.scd.idgen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 发号服务配置。
 *
 * <p>用 {@code @ConfigurationProperties} 而不是 {@code @Value} 绑定——
 * 本项目已经踩过 {@code @Value} 取不到值的坑（网关白名单为空、启动无报错，
 * 表现为连登录都 401）。配置项一旦有多个，就统一走 {@code @ConfigurationProperties}，
 * 它能一次性把整棵配置树绑成对象，写错了至少有 IDEA 提示。
 */
@Data
@ConfigurationProperties(prefix = "app.id-gen")
public class IdGenProperties {

    /**
     * 异步预取线程数。
     *
     * <p>给 2 就够，理由有两个：
     * <ul>
     *   <li>预取本身是<b>低频</b>操作——每个 bizTag 每 step 次发号才发生一次，
     *       step 按官方口径取「高峰 QPS × 600」时，2000 QPS 下每 10 分钟才一次；</li>
     *   <li>不能只给 1：一个 bizTag 的预取若卡在慢查询上，会拖住其它 bizTag 的预取。
     *       而给更多也没用——真正的瓶颈在 MySQL 那一行的行锁上，线程再多也只是排队。</li>
     * </ul>
     *
     * <p>这里刻意<b>不给无界大线程池</b>：万一预取逻辑出问题（比如冷却失效），
     * 小线程池能让冲击被限制在 2 个并发上，而不是瞬间打爆 MySQL。
     */
    private int prefetchThreads = 2;

    /** 启动时是否把 leaf_alloc 里所有 bizTag 的号段预热到内存（对应 Leaf 的 init()） */
    private boolean warmUpOnStartup = true;
}
