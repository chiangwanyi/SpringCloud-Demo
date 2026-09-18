package com.jwy.scd.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jwy.scd.api.auth.dto.LoginDTO;
import com.jwy.scd.api.auth.dto.TokenInfoDTO;
import com.jwy.scd.entity.AuthAccount;

/**
 * 认证账号业务接口。
 * 继承 MyBatis-Plus 的 {@link IService} 直接获得 save / getById / list / updateById / removeById 等通用能力。
 */
public interface IAuthAccountService extends IService<AuthAccount> {

    /** 根据用户名查询（自定义注解 Mapper 实现） */
    AuthAccount getByUsername(String username);

    /** 登录：校验账号密码，成功返回令牌 */
    TokenInfoDTO login(LoginDTO loginDTO);

    /** 校验令牌是否有效 */
    boolean validateToken(String token);

    /** 注销令牌 */
    void logout(String token);
}
