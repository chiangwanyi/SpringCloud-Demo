package com.jwy.scd.service;

import com.jwy.scd.api.auth.dto.LoginDTO;
import com.jwy.scd.api.auth.dto.TokenInfoDTO;

/**
 * 认证业务接口。
 *
 * <p>职责边界（2026-09-20 重构）：本服务不再管理账号、不保存密码、不连数据库。
 * 登录时通过 Feign 调用 service-system 校验凭据，通过后仅负责令牌的签发 / 校验 / 注销。
 */
public interface IAuthService {

    /**
     * 登录：通过 Feign 远程调用 service-system 校验用户名密码，
     * 校验通过后签发令牌（内存令牌，30 分钟过期）。
     *
     * @param loginDTO 登录请求（用户名 + 密码）
     * @return 令牌信息；凭据错误抛出 {@link com.jwy.scd.exception.AuthException}（统一提示，不区分账号不存在/密码错误）
     */
    TokenInfoDTO login(LoginDTO loginDTO);

    /** 校验令牌是否有效 */
    boolean validateToken(String token);

    /** 注销令牌 */
    void logout(String token);
}
