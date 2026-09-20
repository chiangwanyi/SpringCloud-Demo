package com.jwy.scd.controller;

import com.jwy.scd.api.auth.AuthApi;
import com.jwy.scd.api.auth.AuthHeaders;
import com.jwy.scd.api.auth.dto.LoginDTO;
import com.jwy.scd.api.auth.dto.TokenInfoDTO;
import com.jwy.scd.service.IAuthService;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口实现：实现 service-auth-api 中定义的 {@link AuthApi} 契约。
 *
 * <p>HTTP 映射（{@code /api/auth/*}）与参数注解（{@code @RequestBody} / {@code @RequestHeader}）
 * 均继承自接口，Controller 只负责把请求转给服务层。
 *
 * <p>注意这里 <b>没有</b> {@code validateToken} —— 「令牌是否有效」不再由认证服务回答，
 * 而是网关直接读 Redis 判定。详见 {@link AuthApi} 的类注释。
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

    /**
     * 注销：从 {@code Authorization: Bearer <token>} 头解析令牌并删除对应的 Redis 会话。
     *
     * <p>用 {@link AuthHeaders#resolveToken} 而不是在这里手写 {@code substring(7)}，
     * 保证与网关的解析规则完全一致。
     */
    @Override
    public void logout(String authorization) {
        authService.logout(AuthHeaders.resolveToken(authorization));
    }
}
