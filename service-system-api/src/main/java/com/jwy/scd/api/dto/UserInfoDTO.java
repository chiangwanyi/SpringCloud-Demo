package com.jwy.scd.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对外暴露的用户信息 DTO。
 * 注意：刻意不包含 password 等敏感字段，仅返回业务需要的展示信息。
 */
@Data
@Schema(name = "UserInfo", description = "系统用户对外信息（不含敏感字段）")
public class UserInfoDTO {

    @Schema(description = "用户主键 ID", example = "1")
    private Long id;

    @Schema(description = "所属部门 ID", example = "10")
    private Long deptId;

    @Schema(description = "登录用户名", example = "admin")
    private String username;

    @Schema(description = "用户昵称 / 展示名", example = "管理员")
    private String nickname;

    @Schema(description = "账号状态：1=正常，0=禁用", example = "1")
    private Integer status;

    @Schema(description = "创建时间", example = "2026-09-18T10:00:00")
    private LocalDateTime createTime;
}
