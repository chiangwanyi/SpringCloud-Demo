package com.jwy.scd.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jwy.scd.api.dto.PasswordVerifyDTO;
import com.jwy.scd.api.dto.SysUserSaveDTO;
import com.jwy.scd.api.dto.UserInfoDTO;
import com.jwy.scd.entity.SysUser;

import java.util.List;

/**
 * 用户业务接口。
 * 继承 MyBatis-Plus 的 {@link IService} 直接获得 save / getById / list / updateById / removeById 等通用能力。
 */
public interface ISysUserService extends IService<SysUser> {

    /** 根据用户名查询（自定义注解 Mapper 实现） */
    SysUser getByUsername(String username);

    /** 对外返回脱敏后的用户信息（按主键） */
    UserInfoDTO getUserInfo(Long id);

    /** 对外返回脱敏后的用户信息（按用户名） */
    UserInfoDTO getUserInfoByUsername(String username);

    /** 对外返回脱敏后的用户列表 */
    List<UserInfoDTO> listUserInfo();

    /** 新增用户，返回脱敏后的用户信息 */
    UserInfoDTO createUser(SysUserSaveDTO dto);

    /** 修改用户（按主键），返回脱敏后的用户信息 */
    UserInfoDTO updateUser(Long id, SysUserSaveDTO dto);

    /** 逻辑删除用户（del_flag = 1），成功返回 true */
    boolean deleteUser(Long id);

    /**
     * 校验登录密码：用户名 + 密码是否匹配且账号可用。
     * 供认证服务（service-auth）登录时远程调用；密码比对在本服务内部完成，不对外泄露。
     */
    boolean verifyPassword(PasswordVerifyDTO dto);
}
