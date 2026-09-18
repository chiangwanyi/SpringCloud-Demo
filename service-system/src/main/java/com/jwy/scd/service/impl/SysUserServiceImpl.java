package com.jwy.scd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jwy.scd.api.dto.UserInfoDTO;
import com.jwy.scd.entity.SysUser;
import com.jwy.scd.mapper.SysUserMapper;
import com.jwy.scd.service.ISysUserService;
import org.springframework.stereotype.Service;

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
