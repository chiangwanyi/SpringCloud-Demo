package com.jwy.scd.controller;

import com.jwy.scd.api.auth.AuthApi;
import com.jwy.scd.api.auth.dto.AuthAccountDTO;
import com.jwy.scd.api.auth.dto.AuthAccountSaveDTO;
import com.jwy.scd.api.auth.dto.LoginDTO;
import com.jwy.scd.api.auth.dto.TokenInfoDTO;
import com.jwy.scd.service.IAuthAccountService;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证接口实现：实现 service-auth-api 中定义的 {@link AuthApi} 契约。
 * <p>HTTP 映射（/api/auth/*）继承自接口上的 {@code @RequestMapping}，
 * 这里只需关注业务逻辑，保证对外契约与 api 模块完全一致。
 */
@RestController
public class AuthController implements AuthApi {

    private final IAuthAccountService authAccountService;

    public AuthController(IAuthAccountService authAccountService) {
        this.authAccountService = authAccountService;
    }

    @Override
    public TokenInfoDTO login(LoginDTO loginDTO) {
        return authAccountService.login(loginDTO);
    }

    @Override
    public Boolean validateToken(String token) {
        return authAccountService.validateToken(token);
    }

    @Override
    public void logout(String token) {
        authAccountService.logout(token);
    }

    @Override
    public AuthAccountDTO createAccount(AuthAccountSaveDTO dto) {
        return authAccountService.createAccount(dto);
    }

    @Override
    public AuthAccountDTO getAccount(Long id) {
        return authAccountService.getAccount(id);
    }

    @Override
    public List<AuthAccountDTO> listAccounts() {
        return authAccountService.listAccounts();
    }

    @Override
    public AuthAccountDTO updateAccount(Long id, AuthAccountSaveDTO dto) {
        return authAccountService.updateAccount(id, dto);
    }

    @Override
    public void deleteAccount(Long id) {
        authAccountService.deleteAccount(id);
    }
}
