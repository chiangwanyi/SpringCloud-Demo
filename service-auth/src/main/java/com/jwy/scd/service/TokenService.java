package com.jwy.scd.service;

import com.jwy.scd.api.auth.dto.TokenInfoDTO;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 令牌服务（演示级实现）。
 *
 * <p>当前为内存令牌：登录成功后签发 UUID 令牌并保存到内存 Map，附带过期时间；
 * 校验时比对是否存在且未过期，注销即从 Map 移除。
 * <b>生产环境应替换为 JWT（无状态、可校验签名）或结合 Redis 存储（支持集群共享与主动失效）。</b>
 */
@Service
public class TokenService {

    private static final long DEFAULT_EXPIRY_MILLIS = 30L * 60 * 1000; // 默认 30 分钟

    private final Map<String, TokenEntry> tokenStore = new ConcurrentHashMap<>();

    /** 为指定用户签发令牌 */
    public TokenInfoDTO issue(String username) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenStore.put(token, new TokenEntry(username, System.currentTimeMillis() + DEFAULT_EXPIRY_MILLIS));

        TokenInfoDTO dto = new TokenInfoDTO();
        dto.setToken(token);
        dto.setTokenType("Bearer");
        dto.setExpiresIn(DEFAULT_EXPIRY_MILLIS / 1000);
        dto.setUsername(username);
        return dto;
    }

    /** 校验令牌：存在且未过期返回 true，过期则自动清理 */
    public boolean validate(String token) {
        TokenEntry entry = tokenStore.get(token);
        if (entry == null) {
            return false;
        }
        if (entry.expireAt < System.currentTimeMillis()) {
            tokenStore.remove(token);
            return false;
        }
        return true;
    }

    /** 注销令牌 */
    public void revoke(String token) {
        tokenStore.remove(token);
    }

    private static class TokenEntry {
        final String username;
        final long expireAt;

        TokenEntry(String username, long expireAt) {
            this.username = username;
            this.expireAt = expireAt;
        }
    }
}
