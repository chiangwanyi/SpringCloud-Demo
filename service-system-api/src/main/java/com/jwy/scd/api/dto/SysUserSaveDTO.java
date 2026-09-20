package com.jwy.scd.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户新增 / 修改请求 DTO（对外契约）。
 * 与 {@link UserInfoDTO} 不同，这里包含 password 等写入字段；
 * 由实现方 Controller 映射为实体后落库。
 */
@Data
@Schema(name = "SysUserSave", description = "用户新增/修改请求参数")
public class SysUserSaveDTO implements Serializable {

    @Schema(description = "登录用户名", example = "lisi", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @Schema(description = "登录密码（明文，仅演示用）", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Schema(description = "用户昵称 / 展示名", example = "李四")
    private String nickname;

    @Schema(description = "所属部门 ID", example = "1")
    private Long deptId;

    @Schema(description = "账号状态：1=正常，0=禁用", example = "1")
    private Integer status;
}
