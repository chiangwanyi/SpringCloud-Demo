package com.jwy.scd.api;

import com.jwy.scd.api.dto.UserInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * service-system 对外提供的用户服务契约。
 *
 * <p>该接口同时承担两个职责：
 * <ol>
 *     <li>实现方（service-system 模块）用 {@code @RestController} 实现它，提供 HTTP 接口；</li>
 *     <li>调用方（其他微服务）通过 {@code extends SysUserApi} 并加 {@code @FeignClient} 即可获得声明式远程调用。</li>
 * </ol>
 *
 * 注意：这里的 {@code @RequestMapping} 是 Spring MVC 的映射，实现方的 Controller 会继承它，
 * 因此接口路径统一为 {@code /api/sys-user}；{@code @FeignClient} 仅声明远程服务名，
 * 真正生成 Feign 客户端发生在“启用 @EnableFeignClients 的调用方”中。
 */
@FeignClient(name = "service-system")
@RequestMapping("/api/sys-user")
public interface SysUserApi {

    /** 按主键查询用户 */
    @GetMapping("/{id}")
    UserInfoDTO getUserById(@PathVariable("id") Long id);

    /** 按用户名查询用户 */
    @GetMapping("/by-username")
    UserInfoDTO getUserByUsername(@RequestParam("username") String username);

    /** 查询用户列表 */
    @GetMapping("/list")
    List<UserInfoDTO> listUsers();
}
