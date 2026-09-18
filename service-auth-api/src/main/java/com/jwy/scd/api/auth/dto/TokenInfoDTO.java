package com.jwy.scd.api.auth.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 令牌信息 DTO，登录成功后返回给调用方。
 */
@Data
public class TokenInfoDTO implements Serializable {

    /** 令牌字符串 */
    private String token;

    /** 令牌类型，例如 Bearer */
    private String tokenType;

    /** 有效期（秒） */
    private Long expiresIn;

    /** 关联的登录用户名 */
    private String username;
}
