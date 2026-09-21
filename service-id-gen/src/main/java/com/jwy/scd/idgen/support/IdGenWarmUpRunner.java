package com.jwy.scd.idgen.support;

import com.jwy.scd.idgen.service.SegmentIdGen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 启动预热：容器 refresh 完成后，把所有 bizTag 的首段（以及预备段）拉进内存。
 *
 * <p><b>为什么必须是 {@code ApplicationRunner} 而不是 {@code @PostConstruct}</b>：
 * {@code @PostConstruct} 在 Bean 初始化阶段执行，此时事务代理、数据源、
 * MyBatis 的 Mapper 都可能还没装配完；而且一旦这里抛异常会直接让 Bean 创建失败、服务起不来。
 * {@code ApplicationRunner} 在容器完全就绪之后执行，是「启动后做点事」的正确时机。
 */
@Component
@ConditionalOnProperty(name = "app.id-gen.warm-up-on-startup", havingValue = "true", matchIfMissing = true)
public class IdGenWarmUpRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IdGenWarmUpRunner.class);

    private final SegmentIdGen idGen;

    public IdGenWarmUpRunner(SegmentIdGen idGen) {
        this.idGen = idGen;
    }

    @Override
    public void run(ApplicationArguments args) {
        long t0 = System.currentTimeMillis();
        log.info("[id-gen] 启动预热开始");
        // warmUpAll 内部对每个 tag 都做了兜底：失败只 WARN，不会把启动搞挂
        idGen.warmUpAll();
        log.info("[id-gen] 启动预热结束，耗时 {} ms", System.currentTimeMillis() - t0);
    }
}
