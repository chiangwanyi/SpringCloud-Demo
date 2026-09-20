package com.jwy.scd.gateway;

import com.jwy.scd.gateway.config.AuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API 网关启动类。
 *
 * <p>网关只做两件事，不承载任何业务：
 * <ol>
 *     <li><b>路由转发 + 负载均衡</b>：规则全部声明在 {@code src/main/resources/application.yml}
 *         的 {@code spring.cloud.gateway.server.webmvc.routes} 里，无需写任何 Java 路由代码；</li>
 *     <li><b>统一鉴权</b>：{@code TokenAuthFilter} 读取 Redis 会话判断令牌有效性，
 *         并把用户身份注入请求头透传给下游。</li>
 * </ol>
 *
 * <p>外部用户只需访问本服务的地址（默认 {@code http://localhost:8080}），
 * 由网关按路径把请求转发到后端的 service-system(:8081) / service-auth(:8082) / service-order(:8083)。
 *
 * <p><b>注意本类上没有任何 {@code @EnableFeignClients}</b>：鉴权采用 Redis Session Token 方案，
 * 网关直接读 Redis 获取会话，不存在「调用认证服务校验令牌」的远程调用，因此不需要 Feign，
 * 也不需要依赖 service-auth 的 Feign 客户端。
 */
@SpringBootApplication
@EnableDiscoveryClient
// 把 app.auth.* 绑定成强类型配置 bean（白名单 / 会话续期参数）。
// 注意：白名单是 YAML 列表，只能用 @ConfigurationProperties 绑定，不能用 @Value（详见 AuthProperties 注释）。
@EnableConfigurationProperties(AuthProperties.class)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
