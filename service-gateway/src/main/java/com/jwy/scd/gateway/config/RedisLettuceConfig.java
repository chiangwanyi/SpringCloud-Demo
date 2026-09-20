package com.jwy.scd.gateway.config;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.DnsResolvers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 客户端（Lettuce）配置 —— 解决「主机名用 DNS 解析不到、但操作系统解析得到」的问题。
 *
 * <p><b>问题现象</b>：本项目 Redis 主机名是 {@code rxs}。启动后一访问就报
 * <pre>
 *   io.lettuce.core.RedisConnectionException: Unable to connect to rxs/&lt;unresolved&gt;:6379
 *   Caused by: java.net.UnknownHostException: Failed to resolve 'rxs' [A(1)] after 2 queries
 *   Caused by: io.netty.resolver.dns.DnsErrorCauseException: Query failed with NXDOMAIN
 * </pre>
 * 但同一个主机名用 {@code mysql -h rxs}、{@code curl rxs:8848}（Nacos）、甚至 Python 都能连通。
 *
 * <p><b>原因</b>：Lettuce 默认使用 <b>Netty 自带的 DNS 解析器</b>（只做标准 DNS 查询），
 * 它<b>不会</b>走操作系统的解析链路，因此既读不到 hosts 文件，
 * 也不支持 LLMNR / NetBIOS / mDNS 这类局域网名称发现协议。
 * 本机的 {@code rxs} 恰恰是靠 LLMNR 解析到 {@code 192.168.1.45} 的，
 * 而配置的 DNS 服务器上并没有这条 A 记录 → Netty 收到 NXDOMAIN。
 * 相比之下 JDBC（MySQL）、Nacos 客户端用的都是 JDK 的 {@code InetAddress}，所以它们一直正常。
 *
 * <p><b>解决</b>：把 DNS 解析器换成 {@link DnsResolvers#JVM_DEFAULT}，
 * 即使用 JDK / 操作系统的解析器（{@code InetAddress}），行为与其它客户端保持一致。
 *
 * <p>Spring Boot 的 Redis 自动配置会检测容器中的 {@link ClientResources} bean 并使用它，
 * 所以只要定义这个 bean 就生效，不需要手写 {@code LettuceConnectionFactory}。
 *
 * <p><b>提示</b>：{@code service-auth} 里有一份同样的类（两个服务都需要）。
 * 如果将来共用基础设施代码的服务变多，可以考虑抽一个 {@code service-common} 模块来消除这份重复。
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
