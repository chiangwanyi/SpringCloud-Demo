package com.jwy.scd.api;

import com.jwy.scd.api.dto.UserInfoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 *
 * <p>OpenAPI 文档注解（{@code @Tag} / {@code @Operation} / {@code @Parameter}）标在本契约接口上，
 * 由实现方 Controller 继承，保证“契约即文档源”，避免实现模块与 api 模块重复标注。
 */
@FeignClient(name = "service-system")
@Tag(name = "用户管理", description = "系统用户查询接口：按 ID / 用户名查询，以及用户列表")
@RequestMapping("/api/sys-user")
public interface SysUserApi {

    @Operation(summary = "按主键查询用户", description = "根据用户唯一主键 id 返回用户详情")
    @GetMapping("/{id}")
    UserInfoDTO getUserById(@Parameter(description = "用户主键 ID", example = "1") @PathVariable("id") Long id);

    @Operation(summary = "按用户名查询用户", description = "根据登录用户名 username 返回用户详情")
    @GetMapping("/by-username")
    UserInfoDTO getUserByUsername(@Parameter(description = "登录用户名", example = "admin") @RequestParam("username") String username);

    @Operation(summary = "查询用户列表", description = "返回系统中所有用户的列表（演示数据，可能为空）")
    @GetMapping("/list")
    List<UserInfoDTO> listUsers();
}
