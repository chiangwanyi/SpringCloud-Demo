package com.jwy.scd;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.jwy.scd.mapper")
@OpenAPIDefinition(
        info = @Info(
                title = "用户服务 API",
                version = "1.0.0",
                description = "service-system 用户管理微服务 OpenAPI 文档（由 SpringDoc 自动生成）",
                contact = @Contact(name = "springcloud-demo")
        )
)
public class ServiceSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceSystemApplication.class, args);
    }
}
