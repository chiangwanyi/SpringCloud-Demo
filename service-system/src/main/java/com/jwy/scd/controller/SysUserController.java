package com.jwy.scd.controller;

import com.jwy.scd.api.SysUserApi;
import com.jwy.scd.api.dto.PasswordVerifyDTO;
import com.jwy.scd.api.dto.SysUserSaveDTO;
import com.jwy.scd.api.dto.UserInfoDTO;
import com.jwy.scd.service.ISysUserService;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户接口实现：实现 service-system-api 中定义的 {@link SysUserApi} 契约。
 * <p>HTTP 映射（/api/sys-user/*）继承自接口上的 {@code @RequestMapping}，
 * 这里只需关注业务逻辑，保证对外契约与 api 模块完全一致。
 */
@RestController
public class SysUserController implements SysUserApi {

    private final ISysUserService userService;

    public SysUserController(ISysUserService userService) {
        this.userService = userService;
    }

    @Override
    public UserInfoDTO getUserById(Long id) {
        return userService.getUserInfo(id);
    }

    @Override
    public UserInfoDTO getUserByUsername(String username) {
        return userService.getUserInfoByUsername(username);
    }

    @Override
    public List<UserInfoDTO> listUsers() {
        return userService.listUserInfo();
    }

    @Override
    public Boolean verifyPassword(PasswordVerifyDTO dto) {
        return userService.verifyPassword(dto);
    }

    @Override
    public UserInfoDTO createUser(SysUserSaveDTO dto) {
        return userService.createUser(dto);
    }

    @Override
    public UserInfoDTO updateUser(Long id, SysUserSaveDTO dto) {
        return userService.updateUser(id, dto);
    }

    @Override
    public void deleteUser(Long id) {
        userService.deleteUser(id);
    }
}
