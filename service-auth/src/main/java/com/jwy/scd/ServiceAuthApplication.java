package com.jwy.scd;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.jwy.scd.mapper")
@OpenAPIDefinition(
        info = @Info(
                title = "认证服务 API",
                version = "1.0.0",
                description = "service-auth 认证微服务 OpenAPI 文档（由 SpringDoc 自动生成）",
                contact = @Contact(name = "springcloud-demo")
        )
)
public class ServiceAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceAuthApplication.class, args);
    }
}
