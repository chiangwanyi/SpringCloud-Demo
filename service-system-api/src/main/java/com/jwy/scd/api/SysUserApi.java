package com.jwy.scd.api;

import com.jwy.scd.api.dto.SysUserSaveDTO;
import com.jwy.scd.api.dto.UserInfoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * service-system 对外提供的用户服务契约。
 *
 * <p>该接口同时承担两个职责：
 * <ol>
 *     <li>实现方（service-system 模块）用 {@code @RestController} 实现它，提供 HTTP 接口；</li>
 *     <li>调用方（如 service-order）在启动类上加 {@code @EnableFeignClients(clients = SysUserApi.class)}
 *         即可把它注册成 Feign 客户端，直接注入使用，不必再另写一个接口。</li>
 * </ol>
 *
 * <p><b>⚠️ 为什么路径写全在每个方法上，而不是用类级 {@code @RequestMapping("/api/sys-user")}？</b>
 *
 * <p>本契约最初是在接口上同时标 {@code @FeignClient(name = "service-system")} 与
 * {@code @RequestMapping("/api/sys-user")}，靠「实现方 Controller 继承类级映射」来统一前缀。
 * 但 Spring Cloud OpenFeign 5.0.0（对应 Spring Cloud 2025.1.x / Boot 4.0.x）的
 * {@code SpringMvcContract#processAnnotationOnClass} 会显式拒绝这种组合，启动即抛：
 * <pre>
 *   IllegalArgumentException: @RequestMapping annotation not allowed on @FeignClient interfaces
 * </pre>
 * 而且它用的是 {@code findMergedAnnotation}（会沿接口继承链查找），所以
 * 「让消费方 extends 契约接口」也绕不过去——只要继承链上存在类级 {@code @RequestMapping} 就会失败。
 *
 * <p>因此本项目的约定是：<b>带 {@code @FeignClient} 的契约接口上不允许出现类级 {@code @RequestMapping}，
 * 路径前缀一律写在每个方法的映射注解里</b>。实现方的 Controller 依旧继承这些方法级映射，
 * 对外路径与之前完全一致（{@code /api/sys-user/**}），网关路由无需改动。
 *
 * <p>OpenAPI 文档注解（{@code @Tag} / {@code @Operation} / {@code @Parameter}）标在本契约接口上，
 * 由实现方 Controller 继承，保证「契约即文档源」，避免实现模块与 api 模块重复标注。
 */
@FeignClient(name = "service-system")
@Tag(name = "用户管理", description = "系统用户查询与增删改接口")
public interface SysUserApi {

    @Operation(summary = "按主键查询用户", description = "根据用户唯一主键 id 返回用户详情")
    @GetMapping("/api/sys-user/{id}")
    UserInfoDTO getUserById(@Parameter(description = "用户主键 ID", example = "1") @PathVariable("id") Long id);

    @Operation(summary = "按用户名查询用户", description = "根据登录用户名 username 返回用户详情")
    @GetMapping("/api/sys-user/by-username")
    UserInfoDTO getUserByUsername(@Parameter(description = "登录用户名", example = "admin") @RequestParam("username") String username);

    @Operation(summary = "查询用户列表", description = "返回系统中所有用户的列表（演示数据，可能为空）")
    @GetMapping("/api/sys-user/list")
    List<UserInfoDTO> listUsers();

    @Operation(summary = "新增用户", description = "创建一个系统用户，成功返回脱敏后的用户信息")
    @PostMapping("/api/sys-user")
    UserInfoDTO createUser(@RequestBody SysUserSaveDTO dto);

    @Operation(summary = "修改用户", description = "按主键更新用户（用户名/密码/昵称/部门/状态），成功返回脱敏后的用户信息")
    @PutMapping("/api/sys-user/{id}")
    UserInfoDTO updateUser(@Parameter(description = "用户主键 ID", example = "1") @PathVariable("id") Long id,
                           @RequestBody SysUserSaveDTO dto);

    @Operation(summary = "删除用户", description = "按主键逻辑删除用户（del_flag = 1）")
    @DeleteMapping("/api/sys-user/{id}")
    void deleteUser(@Parameter(description = "用户主键 ID", example = "1") @PathVariable("id") Long id);
}
