package com.jwy.scd.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录密码校验请求 DTO。
 *
 * <p>供认证服务（service-auth）登录时通过 Feign 远程调用 service-system 使用：
 * 密码校验统一在用户中心完成，认证服务不再保存任何账号密码。
 * 该 DTO 仅存在于一次校验请求中，不会持久化，也不会在接口响应中返回。
 */
@Data
@Schema(name = "PasswordVerify", description = "登录密码校验请求（用户名 + 密码）")
public class PasswordVerifyDTO {

    @Schema(description = "登录用户名", example = "admin")
    private String username;

    @Schema(description = "密码（明文仅用于本次校验，演示项目未加密）", example = "admin123")
    private String password;
}
