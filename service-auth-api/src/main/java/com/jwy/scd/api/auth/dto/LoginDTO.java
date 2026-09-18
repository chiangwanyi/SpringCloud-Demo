package com.jwy.scd.api.auth.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求 DTO。
 */
@Data
public class LoginDTO implements Serializable {

    private String username;

    private String password;
}
