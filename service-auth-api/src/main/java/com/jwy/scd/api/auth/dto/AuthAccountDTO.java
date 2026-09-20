package com.jwy.scd.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 认证账号对外信息 DTO（脱敏，不含 password）。
 */
@Data
@Schema(name = "AuthAccount", description = "认证账号对外信息（不含密码）")
public class AuthAccountDTO implements Serializable {

    @Schema(description = "账号主键 ID", example = "1")
    private Long id;

    @Schema(description = "登录用户名", example = "admin")
    private String username;

    @Schema(description = "账号状态：1=正常，0=禁用", example = "1")
    private Integer status;

    @Schema(description = "创建时间", example = "2026-09-20T10:00:00")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2026-09-20T10:00:00")
    private LocalDateTime updateTime;
}
