package com.jwy.scd.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 启动预热：容器 refresh 完成后，先向 service-id-gen 领回「当前段 + 预备段」。
 *
 * <p>不预热也能正常发号（{@link SegmentIdGenerator#nextId()} 会懒初始化），
 * 区别只在<b>第一次下单要不要等一次 Feign 往返</b>。预热把这次往返挪到启动阶段，
 * 让「第一单」和「第一万单」的耗时没有区别。
 *
 * <p><b>为什么是 {@code ApplicationRunner} 而不是 {@code @PostConstruct}</b>：
 * Feign 客户端、负载均衡器、Nacos 服务发现都还没就绪的时候去调 service-id-gen 必然失败；
 * 而且 {@code @PostConstruct} 里抛异常会让 Bean 创建失败、整个服务起不来。
 * {@code ApplicationRunner} 在容器完全就绪后执行，是「启动后做点事」的正确时机
 * （踩过的坑，service-id-gen 的 {@code IdGenWarmUpRunner} 里有同样一段注释）。
 *
 * <p>失败只 WARN 不致命：service-id-gen 完全可能比本服务晚启动，
 * 而本服务在没有下单请求时并不需要号段。见 {@link SegmentIdGenerator#warmUp()}。
 */
@Component
@ConditionalOnProperty(name = "app.id-gen.warm-up-on-startup", havingValue = "true", matchIfMissing = true)
public class IdGenWarmUpRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IdGenWarmUpRunner.class);

    private final SegmentIdGenerator idGenerator;

    public IdGenWarmUpRunner(SegmentIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Override
    public void run(ApplicationArguments args) {
        long t0 = System.currentTimeMillis();
        idGenerator.warmUp();
        log.info("[id-gen-client] 启动预热结束（bizTag={}），耗时 {} ms；本地缓冲={}",
                idGenerator.getBizTag(), System.currentTimeMillis() - t0, idGenerator.describe());
    }
}
