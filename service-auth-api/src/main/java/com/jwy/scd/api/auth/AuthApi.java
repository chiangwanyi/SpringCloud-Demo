package com.jwy.scd.api.auth;

import com.jwy.scd.api.auth.dto.LoginDTO;
import com.jwy.scd.api.auth.dto.TokenInfoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * service-auth 对外提供的认证契约。
 *
 * <p>该接口同时承担两个职责：
 * <ol>
 *     <li>实现方（service-auth 模块）用 {@code @RestController} 实现它，提供 HTTP 接口；</li>
 *     <li>调用方（其他微服务）通过 {@code extends AuthApi} 并加 {@code @FeignClient} 即可获得声明式远程调用。</li>
 * </ol>
 *
 * 注意：这里的 {@code @RequestMapping} 是 Spring MVC 的映射，实现方的 Controller 会继承它，
 * 因此接口路径统一为 {@code /api/auth}；{@code @FeignClient} 仅声明远程服务名，
 * 真正生成 Feign 客户端发生在“启用 @EnableFeignClients 的调用方”中。
 *
 * <p>OpenAPI 文档注解标在本契约接口上，由实现方 Controller 继承，保证“契约即文档源”。
 */
@FeignClient(name = "service-auth")
@Tag(name = "认证管理", description = "登录 / 令牌签发 / 校验 / 注销")
@RequestMapping("/api/auth")
public interface AuthApi {

    @Operation(summary = "用户登录", description = "用户名 + 密码登录，成功后返回令牌信息（token / 有效期等）")
    @PostMapping("/login")
    TokenInfoDTO login(@RequestBody(description = "登录请求体（用户名 + 密码）") LoginDTO loginDTO);

    @Operation(summary = "校验令牌", description = "校验令牌是否有效，有效返回 true，过期或不存在返回 false")
    @PostMapping("/validate")
    Boolean validateToken(@Parameter(description = "待校验的令牌字符串", example = "a1b2c3d4-....") @RequestParam("token") String token);

    @Operation(summary = "注销令牌", description = "使指定令牌失效（演示级内存令牌将被移除）")
    @PostMapping("/logout")
    void logout(@Parameter(description = "待注销的令牌字符串", example = "a1b2c3d4-....") @RequestParam("token") String token);
}
