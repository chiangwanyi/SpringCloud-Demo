package com.jwy.scd.service;

import com.jwy.scd.api.auth.dto.LoginDTO;
import com.jwy.scd.api.auth.dto.TokenInfoDTO;

/**
 * 认证业务接口。
 *
 * <p><b>职责边界</b>：本服务不管理账号、不保存密码、不连数据库。
 * <ul>
 *     <li>登录：通过 Feign 调用 service-system 校验凭据，通过后签发令牌并把会话写入 Redis；</li>
 *     <li>注销：删除 Redis 会话，令令牌立即失效。</li>
 * </ul>
 *
 * <p><b>注意这里没有「校验令牌」方法</b>：令牌有效性由 service-gateway 直接读 Redis 判定，
 * 认证服务不再是每个请求的必经之路。详见 {@code AuthApi} 的类注释。
 */
public interface IAuthService {

    /**
     * 登录：远程校验用户名密码，成功后签发会话令牌（写入 Redis，默认 30 分钟过期）。
     *
     * @param loginDTO 登录请求（用户名 + 密码）
     * @return 令牌信息；凭据错误抛出 {@link com.jwy.scd.exception.AuthException}
     *         （统一提示，不区分账号不存在 / 密码错误 / 被禁用，避免账号枚举）
     */
    TokenInfoDTO login(LoginDTO loginDTO);

    /**
     * 注销令牌：删除 Redis 会话。
     *
     * @param token 令牌本体（由 Controller 从 Authorization 头解析后传入）
     */
    void logout(String token);
}
