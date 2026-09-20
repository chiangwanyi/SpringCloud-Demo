package com.jwy.scd.service.impl;

import com.jwy.scd.api.SysUserApi;
import com.jwy.scd.api.auth.dto.LoginDTO;
import com.jwy.scd.api.auth.dto.TokenInfoDTO;
import com.jwy.scd.api.dto.PasswordVerifyDTO;
import com.jwy.scd.exception.AuthException;
import com.jwy.scd.service.IAuthService;
import com.jwy.scd.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 认证业务实现。
 *
 * <p><b>登录链路（统一用户中心模式）</b>：
 * <pre>
 *   客户端 → /api/auth/login → 本服务 → Feign 调用 service-system 的
 *   /api/sys-user/verify-password（查 sys_user，比对密码）→ 返回 true/false
 *   → true 则签发令牌（内存，30 分钟过期）；false 则抛「用户名或密码错误」
 * </pre>
 * 本服务不再保存任何账号密码，密码只在用户中心（service-system）内部比对，
 * 认证侧拿到的只有布尔结果。
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

    private final TokenService tokenService;

    public AuthServiceImpl(SysUserApi sysUserApi, TokenService tokenService) {
        this.sysUserApi = sysUserApi;
        this.tokenService = tokenService;
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
        boolean valid = false;
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
        log.info("登录成功：username={}（凭据由 service-system 校验）", loginDTO.getUsername());

        // ---------- 3. 签发令牌（本服务唯一持久状态：内存 Map，30 分钟过期） ----------
        return tokenService.issue(loginDTO.getUsername());
    }

    @Override
    public boolean validateToken(String token) {
        return tokenService.validate(token);
    }

    @Override
    public void logout(String token) {
        tokenService.revoke(token);
    }
}
