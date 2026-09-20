package com.jwy.scd.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 认证账号新增 / 修改请求 DTO（对外契约）。
 * 与 {@link AuthAccountDTO} 不同，这里包含 password 等写入字段。
 */
@Data
@Schema(name = "AuthAccountSave", description = "认证账号新增/修改请求参数")
public class AuthAccountSaveDTO implements Serializable {

    @Schema(description = "登录用户名", example = "lisi", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @Schema(description = "登录密码（明文，仅演示用）", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Schema(description = "账号状态：1=正常，0=禁用", example = "1")
    private Integer status;
}
