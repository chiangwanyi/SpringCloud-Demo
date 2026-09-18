package com.jwy.scd;

import com.jwy.scd.api.auth.dto.LoginDTO;
import com.jwy.scd.api.auth.dto.TokenInfoDTO;
import com.jwy.scd.entity.AuthAccount;
import com.jwy.scd.exception.AuthException;
import com.jwy.scd.service.IAuthAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * service-auth 登录 / 令牌 流程验证。
 * 使用 H2 内存库（src/main/resources/schema.sql 建表），覆盖登录、令牌签发、校验、注销与异常分支。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuthLoginTest {

    @Autowired
    private IAuthAccountService authService;

    @Test
    void testLoginAndValidate() {
        // 准备账号
        AuthAccount account = new AuthAccount();
        account.setUsername("admin");
        account.setPassword("admin123");
        account.setStatus(1);
        assertTrue(authService.save(account));

        // 登录成功，返回令牌
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("admin");
        loginDTO.setPassword("admin123");
        TokenInfoDTO tokenInfo = authService.login(loginDTO);
        assertNotNull(tokenInfo.getToken());
        assertEquals("Bearer", tokenInfo.getTokenType());
        assertNotNull(tokenInfo.getExpiresIn());

        // 校验令牌有效
        assertTrue(authService.validateToken(tokenInfo.getToken()));

        // 注销后令牌失效
        authService.logout(tokenInfo.getToken());
        assertFalse(authService.validateToken(tokenInfo.getToken()));

        // 密码错误应抛出认证异常
        LoginDTO bad = new LoginDTO();
        bad.setUsername("admin");
        bad.setPassword("wrong");
        assertThrows(AuthException.class, () -> authService.login(bad));
    }
}
