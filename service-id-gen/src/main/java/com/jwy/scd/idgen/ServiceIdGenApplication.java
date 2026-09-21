package com.jwy.scd.idgen;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * service-id-gen 分布式发号服务启动类（Leaf-segment 号段模式自实现版）。
 *
 * <p><b>⚠️ 这里刻意没有 {@code @EnableFeignClients}</b>：本服务是 provider，
 * 用 {@code IdGenController} 实现 {@code IdGenApi}。如果加上 {@code @EnableFeignClients}，
 * Spring Cloud 会为 {@code IdGenApi} 也生成一个 Feign 代理 Bean，它的类型与
 * {@code IdGenController} 相同（都是 IdGenApi），直接导致启动失败：
 * {@code NoUniqueBeanDefinitionException: expected single matching bean but found 2}。
 * 同理 pom 里也没有引入 openfeign。
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.jwy.scd.idgen.mapper")
@OpenAPIDefinition(
        info = @Info(
                title = "分布式发号服务 API",
                version = "1.0.0",
                description = "service-id-gen：Leaf-segment 号段模式发号服务。"
                        + "/api/id/segment 领号段（推荐，业务本地发号零网络开销）、"
                        + "/api/id/next 领单个 ID（服务端持双 buffer）",
                contact = @Contact(name = "springcloud-demo")
        )
)
public class ServiceIdGenApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceIdGenApplication.class, args);
    }
}
