package com.jwy.scd.api.auth;

import com.jwy.scd.api.auth.dto.LoginDTO;
import com.jwy.scd.api.auth.dto.TokenInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
 */
@FeignClient(name = "service-auth")
@RequestMapping("/api/auth")
public interface AuthApi {

    /** 用户名 + 密码登录，成功返回令牌 */
    @PostMapping("/login")
    TokenInfoDTO login(@RequestBody LoginDTO loginDTO);

    /** 校验令牌是否有效 */
    @PostMapping("/validate")
    Boolean validateToken(@RequestParam("token") String token);

    /** 注销令牌 */
    @PostMapping("/logout")
    void logout(@RequestParam("token") String token);
}
