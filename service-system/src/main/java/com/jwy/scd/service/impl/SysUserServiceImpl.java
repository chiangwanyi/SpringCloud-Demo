package com.jwy.scd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jwy.scd.api.dto.SysUserSaveDTO;
import com.jwy.scd.api.dto.UserInfoDTO;
import com.jwy.scd.entity.SysUser;
import com.jwy.scd.mapper.SysUserMapper;
import com.jwy.scd.service.ISysUserService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {

    @Override
    public SysUser getByUsername(String username) {
        return baseMapper.selectByUsername(username);
    }

    @Override
    public UserInfoDTO getUserInfo(Long id) {
        return toDto(getById(id));
    }

    @Override
    public UserInfoDTO getUserInfoByUsername(String username) {
        return toDto(getByUsername(username));
    }

    @Override
    public List<UserInfoDTO> listUserInfo() {
        return list().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public UserInfoDTO createUser(SysUserSaveDTO dto) {
        SysUser user = new SysUser();
        applySaveDto(user, dto);
        save(user);
        return toDto(user);
    }

    @Override
    public UserInfoDTO updateUser(Long id, SysUserSaveDTO dto) {
        SysUser user = getById(id);
        if (user == null) {
            return null;
        }
        applySaveDto(user, dto);
        updateById(user);
        return toDto(user);
    }

    @Override
    public boolean deleteUser(Long id) {
        return removeById(id);
    }

    /** 将写入 DTO 的字段拷贝到实体（null 字段不覆盖，便于部分更新） */
    private void applySaveDto(SysUser user, SysUserSaveDTO dto) {
        if (StringUtils.hasText(dto.getUsername())) {
            user.setUsername(dto.getUsername());
        }
        if (StringUtils.hasText(dto.getPassword())) {
            user.setPassword(dto.getPassword());
        }
        if (StringUtils.hasText(dto.getNickname())) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getDeptId() != null) {
            user.setDeptId(dto.getDeptId());
        }
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
        }
    }

    /** 实体 -> 对外 DTO（过滤 password 等敏感字段） */
    private UserInfoDTO toDto(SysUser user) {
        if (user == null) {
            return null;
        }
        UserInfoDTO dto = new UserInfoDTO();
        dto.setId(user.getId());
        dto.setDeptId(user.getDeptId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setStatus(user.getStatus());
        dto.setCreateTime(user.getCreateTime());
        return dto;
    }
}
