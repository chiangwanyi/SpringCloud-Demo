package com.jwy.scd;

import com.jwy.scd.api.SysUserApi;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * service-order 订单微服务启动类。
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.jwy.scd.mapper")
/*
 * ★ 关键开关：Feign 客户端注册。
 *
 * 这里刻意用 `clients = SysUserApi.class` 精确指定要生成代理的接口，而不是用
 * `basePackages = "com.jwy.scd.api"` 这种按包扫描的写法。原因有两个：
 *
 * 1) 精确、可读：一眼就能看出本服务依赖哪些外部服务，新增依赖时显式添加即可，
 *    不会因为某个 jar 里多了一个 @FeignClient 接口就被悄悄注册。
 *
 * 2) 避免自伤：service-order 自己也要实现 service-order-api 里的 OrderApi / ProductApi，
 *    而这三个契约接口都在 com.jwy.scd.api 包下。如果用按包扫描，Spring Cloud 会为
 *    OrderApi / ProductApi 也生成 Feign 代理 Bean，它们与本模块的
 *    OrderController / ProductController 类型相同，直接导致
 *    "NoUniqueBeanDefinitionException: expected single matching bean but found 2" 启动失败。
 *
 * 同理，被调用方 service-system 也绝不能用宽范围的 @EnableFeignClients，
 * 否则它会为 SysUserApi 生成代理，与自己实现该接口的 SysUserController 撞车。
 */
@EnableFeignClients(clients = SysUserApi.class)
@OpenAPIDefinition(
        info = @Info(
                title = "订单服务 API",
                version = "1.0.0",
                description = "service-order 订单微服务 OpenAPI 文档（由 SpringDoc 自动生成）；"
                        + "下单流程内部通过 Feign 调用 service-system 校验用户",
                contact = @Contact(name = "springcloud-demo")
        )
)
public class ServiceOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceOrderApplication.class, args);
    }
}
