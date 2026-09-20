package com.jwy.scd.api.auth;

import com.jwy.scd.api.auth.dto.AuthAccountDTO;
import com.jwy.scd.api.auth.dto.AuthAccountSaveDTO;
import com.jwy.scd.api.auth.dto.LoginDTO;
import com.jwy.scd.api.auth.dto.TokenInfoDTO;
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
 * service-auth 对外提供的认证契约。
 *
 * <p>该接口同时承担两个职责：
 * <ol>
 *     <li>实现方（service-auth 模块）用 {@code @RestController} 实现它，提供 HTTP 接口；</li>
 *     <li>调用方（其他微服务 / 网关）在启动类上加 {@code @EnableFeignClients(clients = AuthApi.class)}
 *         即可把它注册成 Feign 客户端，直接注入使用。</li>
 * </ol>
 *
 * <p><b>路径写法约定</b>：路径前缀 {@code /api/auth} 写在每个方法上，而不是用类级
 * {@code @RequestMapping("/api/auth")}。原因是 Spring Cloud OpenFeign 5.0.0 的
 * {@code SpringMvcContract} 明确禁止 {@code @FeignClient} 接口上出现类级 {@code @RequestMapping}
 * （详见 {@code SysUserApi} 的类注释）。实现方 Controller 继承方法级映射，对外路径不变。
 *
 * <p><b>修正记录</b>：本契约原先误把 {@code io.swagger.v3.oas.annotations.parameters.RequestBody}
 * 当成了 Spring 的 {@code @RequestBody} 使用，导致实现方 Controller 实际上没有任何请求体绑定注解——
 * 以 JSON 调用登录接口时，{@code LoginDTO} 的字段不会被填充（服务端拿到的是 null 用户名/密码）。
 * 现已改为 Spring 的 {@code org.springframework.web.bind.annotation.RequestBody}。
 * 请求体的说明文字交由 {@code @Operation} 与 DTO 上的 {@code @Schema} 表达。
 *
 * <p>OpenAPI 文档注解标在本契约接口上，由实现方 Controller 继承，保证「契约即文档源」。
 */
@FeignClient(name = "service-auth")
@Tag(name = "认证管理", description = "登录 / 令牌签发 / 校验 / 注销，以及认证账号增删改查")
public interface AuthApi {

    @Operation(summary = "用户登录", description = "请求体为 JSON：{ username, password }，成功后返回令牌信息")
    @PostMapping("/api/auth/login")
    TokenInfoDTO login(@RequestBody LoginDTO loginDTO);

    @Operation(summary = "校验令牌", description = "校验令牌是否有效，有效返回 true，过期或不存在返回 false")
    @PostMapping("/api/auth/validate")
    Boolean validateToken(@Parameter(description = "待校验的令牌字符串", example = "a1b2c3d4-....") @RequestParam("token") String token);

    @Operation(summary = "注销令牌", description = "使指定令牌失效（演示级内存令牌将被移除）")
    @PostMapping("/api/auth/logout")
    void logout(@Parameter(description = "待注销的令牌字符串", example = "a1b2c3d4-....") @RequestParam("token") String token);

    @Operation(summary = "新增账号", description = "请求体为 JSON：{ username, password }，成功返回脱敏后的账号信息")
    @PostMapping("/api/auth/account")
    AuthAccountDTO createAccount(@RequestBody AuthAccountSaveDTO dto);

    @Operation(summary = "查询账号", description = "按主键查询认证账号（脱敏，不含密码）")
    @GetMapping("/api/auth/account/{id}")
    AuthAccountDTO getAccount(@Parameter(description = "账号主键 ID", example = "1") @PathVariable("id") Long id);

    @Operation(summary = "查询账号列表", description = "返回所有认证账号列表（脱敏，不含密码）")
    @GetMapping("/api/auth/account/list")
    List<AuthAccountDTO> listAccounts();

    @Operation(summary = "修改账号", description = "按主键更新账号（用户名/密码/状态），成功返回脱敏后的账号信息")
    @PutMapping("/api/auth/account/{id}")
    AuthAccountDTO updateAccount(@Parameter(description = "账号主键 ID", example = "1") @PathVariable("id") Long id,
                                 @RequestBody AuthAccountSaveDTO dto);

    @Operation(summary = "删除账号", description = "按主键逻辑删除账号（del_flag = 1）")
    @DeleteMapping("/api/auth/account/{id}")
    void deleteAccount(@Parameter(description = "账号主键 ID", example = "1") @PathVariable("id") Long id);
}
