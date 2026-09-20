package com.jwy.scd.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 令牌信息 DTO，登录成功后返回给调用方（前端）。
 *
 * <p>这里除了令牌本身，也把 {@code userId} / {@code nickname} 一并返回：
 * 前端拿到后可以直接渲染「当前登录人」，不必再单独调一次用户接口。
 * 注意这些信息 <b>不</b> 是给后端服务用的 —— 后端服务要用身份信息时，
 * 应当信任网关从 Redis 会话里取出并注入的请求头（见网关的 TokenAuthFilter），
 * 而不是信任客户端传来的任何参数。
 */
@Data
@Schema(name = "TokenInfo", description = "登录成功后返回的令牌信息")
public class TokenInfoDTO implements Serializable {

    @Schema(description = "令牌字符串", example = "3f2a1b9c8d7e6f5a4b3c2d1e0f9a8b7c")
    private String token;

    @Schema(description = "令牌类型，通常为 Bearer", example = "Bearer")
    private String tokenType;

    @Schema(description = "有效期（秒）", example = "1800")
    private Long expiresIn;

    @Schema(description = "用户主键 ID", example = "2")
    private Long userId;

    @Schema(description = "登录用户名（同时也是 Redis 会话 key 的一部分）", example = "zhangsan")
    private String username;

    @Schema(description = "用户昵称 / 展示名", example = "张三")
    private String nickname;
}
