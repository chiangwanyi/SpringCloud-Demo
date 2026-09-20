package com.jwy.scd.api.auth;

import com.jwy.scd.api.auth.dto.LoginDTO;
import com.jwy.scd.api.auth.dto.TokenInfoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * service-auth 对外提供的认证契约。
 *
 * <p>该接口同时承担两个职责：
 * <ol>
 *     <li>实现方（service-auth 模块）用 {@code @RestController} 实现它，提供 HTTP 接口；</li>
 *     <li>调用方（其他微服务 / 网关）在启动类上加 {@code @EnableFeignClients(clients = AuthApi.class)}
 *         即可把它注册成 Feign 客户端，直接注入使用。</li>
 * </ol>
 *
 * <p><b>职责边界（2026-09-20 重构）</b>：本契约只保留「登录 / 校验令牌 / 注销令牌」三个接口。
 * 账号与密码的存储、校验统一由 service-system（sys_user）负责：
 * login 内部通过 Feign 调用 service-system 的 {@code /api/sys-user/verify-password} 完成凭据校验，
 * 校验通过后 service-auth 只签发令牌。原 {@code /api/auth/account/**} 账号 CRUD 已移除
 * （账号管理请直接使用 service-system 的用户接口），service-auth 不再保存任何账号密码、不连数据库。
 *
 * <p><b>路径写法约定</b>：路径前缀 {@code /api/auth} 写在每个方法上，而不是用类级
 * {@code @RequestMapping("/api/auth")}。原因是 Spring Cloud OpenFeign 5.0.0 的
 * {@code SpringMvcContract} 明确禁止 {@code @FeignClient} 接口上出现类级 {@code @RequestMapping}
 * （详见 {@code SysUserApi} 的类注释）。实现方 Controller 继承方法级映射，对外路径不变。
 *
 * <p>OpenAPI 文档注解标在本契约接口上，由实现方 Controller 继承，保证「契约即文档源」。
 */
@FeignClient(name = "service-auth")
@Tag(name = "认证管理", description = "登录 / 令牌签发 / 校验 / 注销")
public interface AuthApi {

    @Operation(summary = "用户登录", description = "请求体为 JSON：{ username, password }，凭据由 service-system 校验，成功后返回令牌信息")
    @PostMapping("/api/auth/login")
    TokenInfoDTO login(@RequestBody LoginDTO loginDTO);

    @Operation(summary = "校验令牌", description = "校验令牌是否有效，有效返回 true，过期或不存在返回 false")
    @PostMapping("/api/auth/validate")
    Boolean validateToken(@Parameter(description = "待校验的令牌字符串", example = "a1b2c3d4-....") @RequestParam("token") String token);

    @Operation(summary = "注销令牌", description = "使指定令牌失效（演示级内存令牌将被移除）")
    @PostMapping("/api/auth/logout")
    void logout(@Parameter(description = "待注销的令牌字符串", example = "a1b2c3d4-....") @RequestParam("token") String token);
}
