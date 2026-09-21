package com.jwy.scd.idgen.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 发号服务的装配。
 */
@Configuration
@EnableConfigurationProperties(IdGenProperties.class)
public class IdGenConfig {

    /**
     * 异步预取线程池。
     *
     * <p>三个刻意的选择：
     * <ol>
     *   <li><b>线程数固定为 2</b>（见 {@link IdGenProperties#getPrefetchThreads()}）——
     *       预取是低频操作，线程多了反而会把 MySQL 那行锁堵死；</li>
     *   <li><b>守护线程</b>：不要因为预取线程还没结束就拖住 JVM 退出；</li>
     *   <li><b>显式声明 destroyMethod</b>：让容器关闭时把线程池收掉。
     *       Spring 对 {@code ExecutorService} 能推断出 shutdown，但这里返回类型是
     *       {@code ExecutorService}，显式写出来更不容易被后人改坏。</li>
     * </ol>
     *
     * <p>用 {@code Executors.newFixedThreadPool} 而不是有界队列 + 拒绝策略：
     * 队列无界意味着 {@code execute()} 不会抛 {@code RejectedExecutionException}，
     * 而「被拒绝」是 {@code SegmentBuffer} 必须处理的边界（会导致预取单飞标志泄漏）。
     * 任务本身是被单飞标志和冷却窗口限住的，堆不起来。
     */
    @Bean(name = "idGenPrefetchExecutor", destroyMethod = "shutdown")
    public ExecutorService idGenPrefetchExecutor(IdGenProperties properties) {
        AtomicInteger seq = new AtomicInteger();
        return Executors.newFixedThreadPool(properties.getPrefetchThreads(), runnable -> {
            Thread t = new Thread(runnable, "id-gen-prefetch-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }
}
