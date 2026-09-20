package com.jwy.scd.controller;

import com.jwy.scd.api.auth.AuthApi;
import com.jwy.scd.api.auth.dto.LoginDTO;
import com.jwy.scd.api.auth.dto.TokenInfoDTO;
import com.jwy.scd.service.IAuthService;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口实现：实现 service-auth-api 中定义的 {@link AuthApi} 契约。
 * <p>HTTP 映射（/api/auth/*）继承自接口上的映射注解，
 * 这里只负责把请求转给服务层，业务逻辑（含跨服务调用）都在 {@code AuthServiceImpl} 中。
 */
@RestController
public class AuthController implements AuthApi {

    private final IAuthService authService;

    public AuthController(IAuthService authService) {
        this.authService = authService;
    }

    @Override
    public TokenInfoDTO login(LoginDTO loginDTO) {
        return authService.login(loginDTO);
    }

    @Override
    public Boolean validateToken(String token) {
        return authService.validateToken(token);
    }

    @Override
    public void logout(String token) {
        authService.logout(token);
    }
}
