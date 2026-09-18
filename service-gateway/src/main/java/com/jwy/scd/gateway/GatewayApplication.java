package com.jwy.scd.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API 网关启动类。
 *
 * <p>网关本身不承载业务，只做「统一入口 + 路由转发」。所有路由规则都在
 * {@code src/main/resources/application.yml} 的 {@code spring.cloud.gateway.server.webmvc.routes} 中声明，
 * 无需在这里写任何 Java 路由代码。</p>
 *
 * <p>外部用户只需访问本服务的地址（默认 {@code http://localhost:8080}），
 * 由网关按路径把请求转发到后端的 service-system(:8081) / service-auth(:8082)。</p>
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
