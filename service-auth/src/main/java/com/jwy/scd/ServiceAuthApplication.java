package com.jwy.scd;

import com.jwy.scd.api.SysUserApi;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * service-auth 认证微服务启动类。
 *
 * <p><b>定位</b>：统一的「登录入口 + 会话签发方」。不连数据库、不保存任何账号密码，
 * 职责只有两条：
 * <ol>
 *     <li><b>登录</b>：远程调用 service-system 校验凭据（密码比对只在用户中心内部进行），
 *         通过后把会话写入 <b>Redis</b>（{@code auth:session:<token>}，库 2），返回令牌；</li>
 *     <li><b>注销</b>：删除 Redis 会话，令令牌立即失效（不透明令牌可即时吊销）。</li>
 * </ol>
 *
 * <p><b>注意：本服务不提供「校验令牌」接口。</b>每一次请求的鉴权都由 service-gateway
 * 直接读 Redis 完成，认证服务不在请求主链路上 —— 因此 auth 服务重启/短时不可用
 * 不会导致已登录用户的请求失败。
 */
@SpringBootApplication
@EnableDiscoveryClient
/*
 * ★ 关键开关：Feign 客户端注册。
 *
 * 这里刻意用 `clients = SysUserApi.class` 精确指定要生成代理的接口，而不是用
 * `basePackages = "com.jwy.scd.api"` 这种按包扫描的写法。原因有两个：
 *
 * 1) 精确、可读：一眼就能看出本服务依赖哪些外部服务（service-system），
 *    不会因为某个 jar 里多了一个 @FeignClient 接口就被悄悄注册。
 *
 * 2) 避免自伤：service-auth 自己也要实现 service-auth-api 里的 AuthApi，
 *    而 AuthApi 也在 com.jwy.scd.api 包下。如果用按包扫描，Spring Cloud 会为
 *    AuthApi 也生成 Feign 代理 Bean，它与本模块的 AuthController 类型相同，
 *    直接导致 "NoUniqueBeanDefinitionException: expected single matching bean but found 2"
 *    启动失败。
 *
 * 同理，被调用方 service-system 也绝不能用宽范围的 @EnableFeignClients，
 * 否则它会为 SysUserApi 生成代理，与自己实现该接口的 SysUserController 撞车。
 */
@EnableFeignClients(clients = SysUserApi.class)
@OpenAPIDefinition(
        info = @Info(
                title = "认证服务 API",
                version = "1.0.0",
                description = "service-auth 认证微服务 OpenAPI 文档（由 SpringDoc 自动生成）；"
                        + "登录凭据校验通过 Feign 远程调用 service-system 完成，本服务不保存账号密码；"
                        + "登录成功后会话写入 Redis（库 2），令牌有效性由网关读 Redis 判定，本服务不提供校验接口",
                contact = @Contact(name = "springcloud-demo")
        )
)
public class ServiceAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceAuthApplication.class, args);
    }
}
