package com.jwy.scd.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对外暴露的用户信息 DTO。
 * 注意：刻意不包含 password 等敏感字段，仅返回业务需要的展示信息。
 */
@Data
public class UserInfoDTO {

    private Long id;

    private Long deptId;

    private String username;

    private String nickname;

    private Integer status;

    private LocalDateTime createTime;
}
