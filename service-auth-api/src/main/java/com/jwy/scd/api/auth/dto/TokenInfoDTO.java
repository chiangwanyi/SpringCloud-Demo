package com.jwy.scd.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 令牌信息 DTO，登录成功后返回给调用方。
 */
@Data
@Schema(name = "TokenInfo", description = "登录成功后返回的令牌信息")
public class TokenInfoDTO implements Serializable {

    @Schema(description = "令牌字符串", example = "a1b2c3d4-0000-1111-2222-333344445555")
    private String token;

    @Schema(description = "令牌类型，通常为 Bearer", example = "Bearer")
    private String tokenType;

    @Schema(description = "有效期（秒）", example = "1800")
    private Long expiresIn;

    @Schema(description = "关联的登录用户名", example = "admin")
    private String username;
}
