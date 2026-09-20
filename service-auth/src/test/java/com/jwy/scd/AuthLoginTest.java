package com.jwy.scd;

import com.jwy.scd.api.SysUserApi;
import com.jwy.scd.api.auth.dto.LoginDTO;
import com.jwy.scd.api.auth.dto.TokenInfoDTO;
import com.jwy.scd.api.dto.PasswordVerifyDTO;
import com.jwy.scd.api.dto.UserInfoDTO;
import com.jwy.scd.exception.AuthException;
import com.jwy.scd.service.IAuthService;
import com.jwy.scd.service.SessionTokenService;
import com.jwy.scd.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * service-auth 登录流程单元测试（纯 Mockito）。
 *
 * <p>凭据校验与用户查询都要通过 Feign 走 service-system，单测中 mock 掉 {@link SysUserApi}；
 * 会话写入 Redis，单测中 mock 掉 {@link SessionTokenService}（它自己的行为由
 * {@link SessionTokenServiceTest} 覆盖）。本测试只关心 AuthServiceImpl 的编排逻辑：
 * <ul>
 *     <li>凭据通过 → 查用户 → 用 userId/username/nickname 签发会话；</li>
 *     <li>凭据失败 / 远程异常 / 校验通过但查不到用户 → 统一抛「用户名或密码错误」或「稍后重试」；</li>
 *     <li>参数为空 → 不发起任何远程调用；</li>
 *     <li>注销 → 把令牌透传给 SessionTokenService。</li>
 * </ul>
 */
class AuthLoginTest {

    private final SysUserApi sysUserApi = mock(SysUserApi.class);

    private final SessionTokenService sessionTokenService = mock(SessionTokenService.class);

    private final IAuthService authService = new AuthServiceImpl(sysUserApi, sessionTokenService);

    private static LoginDTO login(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    private static UserInfoDTO user(long id, String username, String nickname) {
        UserInfoDTO dto = new UserInfoDTO();
        dto.setId(id);
        dto.setUsername(username);
        dto.setNickname(nickname);
        return dto;
    }

    @Test
    void loginIssuesSessionWithUserIdAndNickname() {
        when(sysUserApi.verifyPassword(any(PasswordVerifyDTO.class))).thenReturn(Boolean.TRUE);
        when(sysUserApi.getUserByUsername("zhangsan")).thenReturn(user(2L, "zhangsan", "张三"));
        when(sessionTokenService.issue(anyLong(), anyString(), anyString())).thenReturn(new TokenInfoDTO());

        authService.login(login("zhangsan", "123456"));

        // 会话必须以「用户中心返回的」userId / nickname 签发，而不是只存用户名
        ArgumentCaptor<Long> userId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> username = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nickname = ArgumentCaptor.forClass(String.class);
        verify(sessionTokenService).issue(userId.capture(), username.capture(), nickname.capture());
        assertEquals(2L, userId.getValue());
        assertEquals("zhangsan", username.getValue());
        assertEquals("张三", nickname.getValue());
    }

    @Test
    void loginRejectedWhenCredentialInvalid() {
        when(sysUserApi.verifyPassword(any(PasswordVerifyDTO.class))).thenReturn(Boolean.FALSE);

        AuthException ex = assertThrows(AuthException.class, () -> authService.login(login("admin", "wrong")));
        assertEquals("用户名或密码错误", ex.getMessage());

        // 凭据都没过，不应该去查用户、更不应该签发会话
        verify(sysUserApi, never()).getUserByUsername(anyString());
        verify(sessionTokenService, never()).issue(anyLong(), anyString(), anyString());
    }

    @Test
    void loginRejectedWhenVerifyServiceDown() {
        when(sysUserApi.verifyPassword(any(PasswordVerifyDTO.class)))
                .thenThrow(new RuntimeException("connection refused"));

        AuthException ex = assertThrows(AuthException.class, () -> authService.login(login("admin", "admin123")));
        // 不向客户端暴露「service-system 不可用」，但提示可以重试
        assertTrue(ex.getMessage().contains("稍后重试"));
        verify(sessionTokenService, never()).issue(anyLong(), anyString(), anyString());
    }

    @Test
    void loginRejectedWhenUserDisappearedAfterVerify() {
        // 边界场景：校验凭据刚通过，用户就被删了（用户中心数据异常）
        when(sysUserApi.verifyPassword(any(PasswordVerifyDTO.class))).thenReturn(Boolean.TRUE);
        when(sysUserApi.getUserByUsername("admin")).thenReturn(null);

        AuthException ex = assertThrows(AuthException.class, () -> authService.login(login("admin", "admin123")));
        assertTrue(ex.getMessage().contains("稍后重试"));
        verify(sessionTokenService, never()).issue(anyLong(), anyString(), anyString());
    }

    @Test
    void loginRejectedWhenUserQueryThrows() {
        when(sysUserApi.verifyPassword(any(PasswordVerifyDTO.class))).thenReturn(Boolean.TRUE);
        when(sysUserApi.getUserByUsername("admin")).thenThrow(new RuntimeException("timeout"));

        AuthException ex = assertThrows(AuthException.class, () -> authService.login(login("admin", "admin123")));
        assertTrue(ex.getMessage().contains("稍后重试"));
        verify(sessionTokenService, never()).issue(anyLong(), anyString(), anyString());
    }

    @Test
    void loginRejectedWithBlankFieldsWithoutRemoteCall() {
        assertThrows(AuthException.class, () -> authService.login(null));
        assertThrows(AuthException.class, () -> authService.login(new LoginDTO()));
        assertThrows(AuthException.class, () -> authService.login(login("admin", " ")));

        // 参数校验必须在最前面，不能白白打一次远程调用
        verify(sysUserApi, never()).verifyPassword(any(PasswordVerifyDTO.class));
        verify(sessionTokenService, never()).issue(anyLong(), anyString(), anyString());
    }

    @Test
    void logoutDelegatesTokenToSessionStore() {
        authService.logout("abc123");
        verify(sessionTokenService).revoke("abc123");
    }
}
