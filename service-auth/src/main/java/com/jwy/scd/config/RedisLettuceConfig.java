package com.jwy.scd.config;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.DnsResolvers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 客户端（Lettuce）配置 —— 让 Lettuce 改用 JVM / 操作系统的 DNS 解析器。
 *
 * <p><b>为什么必须加这个类？</b>
 * Lettuce 默认使用 Netty 自带的 DNS 解析器（只做标准 DNS 查询），
 * 不走操作系统的解析链路：既不读 hosts 文件，也不支持 LLMNR / NetBIOS / mDNS。
 * 本项目 Redis 主机名 {@code rxs} 是靠局域网名称发现（LLMNR）解析到 {@code 192.168.1.45} 的，
 * DNS 服务器上没有这条记录，于是会报：
 * <pre>
 *   Unable to connect to rxs/&lt;unresolved&gt;:6379
 *   Caused by: UnknownHostException: Failed to resolve 'rxs' [A(1)] after 2 queries
 *   Caused by: DnsErrorCauseException: Query failed with NXDOMAIN
 * </pre>
 * 而 JDBC / Nacos 客户端用的是 JDK 的 {@code InetAddress}，所以它们一直连得上 —— 只有 Lettuce 会挂。
 *
 * <p>{@link DnsResolvers#JVM_DEFAULT} 即「用 JDK 默认解析器」，
 * 与其它客户端的解析行为统一。Spring Boot 的 Redis 自动配置会自动采用容器中的
 * {@link ClientResources} bean，无需手写 {@code LettuceConnectionFactory}。
 *
 * <p>注：{@code service-gateway} 中有一份同样的类（网关也要读 Redis）。
 */
@Configuration
public class RedisLettuceConfig {

    @Bean(destroyMethod = "shutdown")
    public ClientResources redisClientResources() {
        return DefaultClientResources.builder()
                .dnsResolver(DnsResolvers.JVM_DEFAULT)
                .build();
    }
}
