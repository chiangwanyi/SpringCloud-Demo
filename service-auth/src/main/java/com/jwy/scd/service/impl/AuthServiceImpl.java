package com.jwy.scd.service.impl;

import com.jwy.scd.api.SysUserApi;
import com.jwy.scd.api.auth.dto.LoginDTO;
import com.jwy.scd.api.auth.dto.TokenInfoDTO;
import com.jwy.scd.api.dto.PasswordVerifyDTO;
import com.jwy.scd.api.dto.UserInfoDTO;
import com.jwy.scd.exception.AuthException;
import com.jwy.scd.service.IAuthService;
import com.jwy.scd.service.SessionTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 认证业务实现。
 *
 * <p><b>登录链路（统一用户中心 + Redis 会话）</b>：
 * <pre>
 *   客户端 → POST /api/auth/login
 *        → 本服务 Feign 调用 service-system /api/sys-user/verify-password（查 sys_user 比对密码）
 *        → 再 Feign 调用 service-system /api/sys-user/by-username（取 userId / nickname）
 *        → 组装 AuthSession 写入 Redis（key=auth:session:&lt;token&gt;，TTL=30min）
 *        → 返回 TokenInfoDTO
 * </pre>
 *
 * <p>本服务不保存任何账号密码，密码只在用户中心（service-system）内部比对。
 *
 * <p><b>为什么登录要调两次 service-system？</b>
 * {@code verifyPassword} 刻意只返回布尔值，不返回用户详情（避免密码相关信息离开用户中心）。
 * 但要写入 Redis 的会话需要 {@code userId}，所以校验通过后再查一次用户信息。
 * 两次调用都只发生在<b>登录这一刻</b>，不在请求主链路上，代价可以接受。
 * 若将来想省掉第二次调用，可把 {@code verifyPassword} 改为返回「校验结果 + 用户摘要」的组合 DTO，
 * 但那会让用户中心暴露更多信息，属于取舍而非纯优化。
 */
@Service
public class AuthServiceImpl implements IAuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    /**
     * ★ Feign 客户端代理（指向 service-system）。
     *
     * <p>由启动类上的 {@code @EnableFeignClients(clients = SysUserApi.class)}
     * 依据 service-system-api 里的 {@code @FeignClient(name = "service-system")} 声明生成。
     * 调用它的方法就像调用本地方法，底层自动完成
     * 「向 Nacos 查 service-system 实例 → LoadBalancer 选一个 → 拼 HTTP 请求 → 反序列化响应」。
     */
    private final SysUserApi sysUserApi;

    private final SessionTokenService sessionTokenService;

    public AuthServiceImpl(SysUserApi sysUserApi, SessionTokenService sessionTokenService) {
        this.sysUserApi = sysUserApi;
        this.sessionTokenService = sessionTokenService;
    }

    @Override
    public TokenInfoDTO login(LoginDTO loginDTO) {
        // ---------- 1. 基础参数校验 ----------
        if (loginDTO == null
                || !StringUtils.hasText(loginDTO.getUsername())
                || !StringUtils.hasText(loginDTO.getPassword())) {
            throw new AuthException("用户名或密码错误");
        }

        // ---------- 2. ★ 跨服务调用：远程校验用户名密码（service-system 内部比对，不泄露任何用户信息） ----------
        PasswordVerifyDTO verify = new PasswordVerifyDTO();
        verify.setUsername(loginDTO.getUsername());
        verify.setPassword(loginDTO.getPassword());
        boolean valid;
        try {
            valid = Boolean.TRUE.equals(sysUserApi.verifyPassword(verify));
        } catch (Exception ex) {
            // service-system 不可用等异常统一转换成「凭据错误」提示，避免向客户端暴露内部细节；
            // 同时这也是后续引入 Sentinel 熔断降级的天然切入点。
            log.error("跨服务调用失败：service-system.verifyPassword(username={})", loginDTO.getUsername(), ex);
            throw new AuthException("用户名或密码错误，请稍后重试");
        }
        if (!valid) {
            // 统一提示：不区分「账号不存在 / 被禁用 / 密码错误」，避免泄露账号是否存在
            throw new AuthException("用户名或密码错误");
        }
        log.info("凭据校验通过：username={}（由 service-system 校验）", loginDTO.getUsername());

        // ---------- 3. ★ 取用户详情：会话里要存 userId / nickname，供网关透传给下游 ----------
        UserInfoDTO user;
        try {
            user = sysUserApi.getUserByUsername(loginDTO.getUsername());
        } catch (Exception ex) {
            log.error("跨服务调用失败：service-system.getUserByUsername(username={})", loginDTO.getUsername(), ex);
            throw new AuthException("登录失败，请稍后重试");
        }
        if (user == null || user.getId() == null) {
            // 凭据校验刚通过却查不到用户，说明用户中心数据异常（如校验与查询之间被删）
            log.error("凭据校验通过但查不到用户：username={}", loginDTO.getUsername());
            throw new AuthException("登录失败，请稍后重试");
        }

        // ---------- 4. 签发令牌：把会话写入 Redis ----------
        return sessionTokenService.issue(user.getId(), user.getUsername(), user.getNickname());
    }

    @Override
    public void logout(String token) {
        sessionTokenService.revoke(token);
    }
}
