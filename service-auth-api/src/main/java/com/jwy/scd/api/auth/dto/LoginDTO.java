package com.jwy.scd.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求 DTO。
 */
@Data
@Schema(name = "LoginRequest", description = "登录请求参数")
public class LoginDTO implements Serializable {

    @Schema(description = "登录用户名", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @Schema(description = "登录密码（明文，仅演示用；生产应走 HTTPS + 加密传输）", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
