package com.jwy.scd.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sentinel 相关配置。
 *
 * <p><b>⚠️ 为什么必须手动注册 {@link SentinelResourceAspect}？</b>
 * {@code @SentinelResource} 注解本身不生效——它依赖一个 AspectJ 切面（{@link SentinelResourceAspect}）
 * 在方法调用处做拦截（AOP）。但 {@code sentinel-annotation-aspectj} 这个 jar 里<strong>没有</strong>
 * 提供 spring.factories / 自动配置去注册这个切面，Spring 不会自动把它变成 Bean。
 * 结果就是：注解写了、编译运行都正常，但<strong>静默失效</strong>——方法照常执行，fallback 永远不触发。
 *
 * <p>所以必须在这里显式声明一个 {@link SentinelResourceAspect} 的 Bean，{@code @SentinelResource}
 * 才会真正被 AspectJ 切面拦截。
 */
@Configuration
public class SentinelConfig {

    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }
}
