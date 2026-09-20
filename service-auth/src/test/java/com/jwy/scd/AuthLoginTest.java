package com.jwy.scd;

import com.jwy.scd.api.SysUserApi;
import com.jwy.scd.api.auth.dto.LoginDTO;
import com.jwy.scd.api.auth.dto.TokenInfoDTO;
import com.jwy.scd.api.dto.PasswordVerifyDTO;
import com.jwy.scd.exception.AuthException;
import com.jwy.scd.service.IAuthService;
import com.jwy.scd.service.TokenService;
import com.jwy.scd.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * service-auth 登录 / 令牌 流程验证（纯单元测试）。
 *
 * <p>登录链路的凭据校验已改为通过 Feign 远程调用 service-system（verifyPassword），
 * 单测中用 Mockito mock 掉 {@link SysUserApi}，只验证本服务的业务逻辑：
 * 凭据校验通过 → 签发令牌；凭据错误 / 远程异常 → 统一抛「用户名或密码错误」；
 * 令牌校验 / 注销的正常与异常分支。
 *
 * <p>真实 Feign 调用链（Nacos 发现 + service-system 在线）属于端到端场景，
 * 需启动全部服务后验证，不属于本单元测试范围。
 */
class AuthLoginTest {

    private final SysUserApi sysUserApi = mock(SysUserApi.class);
    private final TokenService tokenService = new TokenService();
    private final IAuthService authService = new AuthServiceImpl(sysUserApi, tokenService);

    @Test
    void testLoginValidateAndLogout() {
        // 凭据校验通过（service-system 返回 true）
        when(sysUserApi.verifyPassword(any(PasswordVerifyDTO.class))).thenReturn(Boolean.TRUE);

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("admin");
        loginDTO.setPassword("admin123");
        TokenInfoDTO tokenInfo = authService.login(loginDTO);

        assertNotNull(tokenInfo.getToken());
        assertEquals("Bearer", tokenInfo.getTokenType());
        assertNotNull(tokenInfo.getExpiresIn());
        assertEquals("admin", tokenInfo.getUsername());

        // 校验令牌有效
        assertTrue(authService.validateToken(tokenInfo.getToken()));

        // 注销后令牌失效
        authService.logout(tokenInfo.getToken());
        assertFalse(authService.validateToken(tokenInfo.getToken()));
    }

    @Test
    void testLoginWithWrongPassword() {
        // 凭据校验失败（service-system 返回 false）→ 统一抛「用户名或密码错误」
        when(sysUserApi.verifyPassword(any(PasswordVerifyDTO.class))).thenReturn(Boolean.FALSE);

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("admin");
        loginDTO.setPassword("wrong");
        AuthException ex = assertThrows(AuthException.class, () -> authService.login(loginDTO));
        assertEquals("用户名或密码错误", ex.getMessage());
    }

    @Test
    void testLoginWithBlankFields() {
        // 空用户名 / 空密码：不发起远程调用，直接抛异常
        LoginDTO blank = new LoginDTO();
        assertThrows(AuthException.class, () -> authService.login(blank));

        LoginDTO noPassword = new LoginDTO();
        noPassword.setUsername("admin");
        assertThrows(AuthException.class, () -> authService.login(noPassword));
    }

    @Test
    void testLoginWhenRemoteServiceDown() {
        // service-system 不可用（Feign 抛异常）→ 不暴露内部细节，同样统一提示
        when(sysUserApi.verifyPassword(any(PasswordVerifyDTO.class)))
                .thenThrow(new RuntimeException("connection refused"));

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("admin");
        loginDTO.setPassword("admin123");
        AuthException ex = assertThrows(AuthException.class, () -> authService.login(loginDTO));
        assertTrue(ex.getMessage().contains("稍后重试"));
    }
}
