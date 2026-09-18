package com.jwy.scd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jwy.scd.api.auth.dto.LoginDTO;
import com.jwy.scd.api.auth.dto.TokenInfoDTO;
import com.jwy.scd.entity.AuthAccount;
import com.jwy.scd.exception.AuthException;
import com.jwy.scd.mapper.AuthAccountMapper;
import com.jwy.scd.service.IAuthAccountService;
import com.jwy.scd.service.TokenService;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AuthAccountServiceImpl extends ServiceImpl<AuthAccountMapper, AuthAccount> implements IAuthAccountService {

    private final TokenService tokenService;

    public AuthAccountServiceImpl(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public AuthAccount getByUsername(String username) {
        return baseMapper.selectByUsername(username);
    }

    @Override
    public TokenInfoDTO login(LoginDTO loginDTO) {
        AuthAccount account = getByUsername(loginDTO.getUsername());
        if (account == null || account.getStatus() == null || account.getStatus() != 1) {
            throw new AuthException("用户名或密码错误");
        }
        if (!Objects.equals(loginDTO.getPassword(), account.getPassword())) {
            throw new AuthException("用户名或密码错误");
        }
        return tokenService.issue(account.getUsername());
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
