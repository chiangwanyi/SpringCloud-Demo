package com.jwy.scd.api.auth;

import com.jwy.scd.api.auth.dto.LoginDTO;
import com.jwy.scd.api.auth.dto.TokenInfoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * service-auth 对外提供的认证契约。
 *
 * <p>该接口同时承担两个职责：
 * <ol>
 *     <li>实现方（service-auth 模块）用 {@code @RestController} 实现它，提供 HTTP 接口；</li>
 *     <li>调用方（其他微服务）在启动类上加 {@code @EnableFeignClients(clients = AuthApi.class)}
 *         即可把它注册成 Feign 客户端，直接注入使用。</li>
 * </ol>
 *
 * <p><b>⚠️ 契约里只有「登录」和「注销」两个 HTTP 接口，没有「校验令牌」接口 —— 这是刻意的。</b>
 *
 * <p>本项目采用 <b>Redis Session Token</b> 鉴权方案：登录成功后 service-auth 把会话
 * （见 {@link com.jwy.scd.api.auth.session.AuthSession}）写入 Redis，之后每一次请求的鉴权
 * 都由 <b>service-gateway 直接读 Redis</b> 完成，<b>不再有任何「调接口问 auth 服务令牌是否有效」的远程调用</b>。
 *
 * <p>为什么废掉 {@code POST /api/auth/validate} 这类接口？
 * <ol>
 *     <li><b>多一次同步 RPC</b>：每个请求都要多跳一次网络（网关 → auth），网关吞吐被 auth 拖住；</li>
 *     <li><b>可用性被绑定</b>：auth 服务一挂，所有已登录用户的请求全部失败（全站不可用）；</li>
 *     <li><b>信息量不足</b>：这类接口通常只返回 {@code true/false}，网关拿不到「当前是谁」，
 *         无法把用户身份透传给下游业务服务，下游做数据权限时寸步难行；</li>
 *     <li><b>本来就是不该暴露的内部接口</b>：它只为「网关远程问票」而存在，属于自己造出来的中间层。</li>
 * </ol>
 * 改用「共享 Redis 读会话」后，上述四个问题一次性消失：0 次额外 RPC、网关可独立工作、
 * 能拿到完整会话信息、且不需要任何额外的服务间接口。
 *
 * <p><b>为什么「注销」仍然是一个 HTTP 接口？</b>
 * 因为「用户主动退出登录」是真实的业务动作（要删除 Redis key），
 * 与「每次请求的令牌校验」完全不是一类需求，不能混为一谈。
 *
 * <p><b>路径写法约定</b>：路径前缀 {@code /api/auth} 写在每个方法上，而不是用类级
 * {@code @RequestMapping("/api/auth")}。原因是 Spring Cloud OpenFeign 5.0.0 的
 * {@code SpringMvcContract} 明确禁止 {@code @FeignClient} 接口上出现类级 {@code @RequestMapping}
 * （详见 {@code SysUserApi} 的类注释）。实现方 Controller 继承方法级映射，对外路径不变。
 *
 * <p>OpenAPI 文档注解标在本契约接口上，由实现方 Controller 继承，保证「契约即文档源」。
 */
@FeignClient(name = "service-auth")
@Tag(name = "认证管理", description = "登录 / 令牌签发 / 注销（令牌有效性由网关读 Redis 判定，不提供校验接口）")
public interface AuthApi {

    @Operation(summary = "用户登录",
            description = "请求体为 JSON：{ username, password }。凭据由 service-system 校验，"
                    + "成功后签发令牌并把会话写入 Redis（库 2），返回令牌信息")
    @PostMapping("/api/auth/login")
    TokenInfoDTO login(@RequestBody LoginDTO loginDTO);

    /**
     * 注销令牌：删除 Redis 中的会话，令该令牌立即失效。
     *
     * <p>令牌从 {@code Authorization: Bearer <token>} 请求头读取，不再用 query 参数 ——
     * 令牌出现在 URL 里会被写进 access log、浏览器历史、Referer 头，是安全反模式。
     * 本接口不在网关白名单内，因此调用方必须先持有一个有效令牌（也就只有会话持有者本人能注销自己的会话）。
     */
    @Operation(summary = "注销令牌",
            description = "从 Authorization: Bearer <token> 头读取令牌，删除 Redis 会话使其立即失效")
    @PostMapping("/api/auth/logout")
    void logout(@Parameter(description = "形如 `Bearer <token>` 的认证头")
                @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);
}
