package com.jwy.scd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jwy.scd.api.auth.dto.AuthAccountDTO;
import com.jwy.scd.api.auth.dto.AuthAccountSaveDTO;
import com.jwy.scd.api.auth.dto.LoginDTO;
import com.jwy.scd.api.auth.dto.TokenInfoDTO;
import com.jwy.scd.entity.AuthAccount;
import com.jwy.scd.exception.AuthException;
import com.jwy.scd.mapper.AuthAccountMapper;
import com.jwy.scd.service.IAuthAccountService;
import com.jwy.scd.service.TokenService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    @Override
    public AuthAccountDTO createAccount(AuthAccountSaveDTO dto) {
        AuthAccount account = new AuthAccount();
        applySaveDto(account, dto);
        save(account);
        return toDto(account);
    }

    @Override
    public AuthAccountDTO getAccount(Long id) {
        return toDto(getById(id));
    }

    @Override
    public List<AuthAccountDTO> listAccounts() {
        return list().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public AuthAccountDTO updateAccount(Long id, AuthAccountSaveDTO dto) {
        AuthAccount account = getById(id);
        if (account == null) {
            return null;
        }
        applySaveDto(account, dto);
        updateById(account);
        return toDto(account);
    }

    @Override
    public void deleteAccount(Long id) {
        removeById(id);
    }

    /** 将写入 DTO 的字段拷贝到实体（null 字段不覆盖，便于部分更新） */
    private void applySaveDto(AuthAccount account, AuthAccountSaveDTO dto) {
        if (StringUtils.hasText(dto.getUsername())) {
            account.setUsername(dto.getUsername());
        }
        if (StringUtils.hasText(dto.getPassword())) {
            account.setPassword(dto.getPassword());
        }
        if (dto.getStatus() != null) {
            account.setStatus(dto.getStatus());
        }
    }

    /** 实体 -> 对外 DTO（过滤 password 等敏感字段） */
    private AuthAccountDTO toDto(AuthAccount account) {
        if (account == null) {
            return null;
        }
        AuthAccountDTO dto = new AuthAccountDTO();
        dto.setId(account.getId());
        dto.setUsername(account.getUsername());
        dto.setStatus(account.getStatus());
        dto.setCreateTime(account.getCreateTime());
        dto.setUpdateTime(account.getUpdateTime());
        return dto;
    }
}
