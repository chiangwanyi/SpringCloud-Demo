package com.jwy.scd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jwy.scd.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper。
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper} 即可获得通用 CRUD（无需 XML、无需手写 SQL）；
 * 对于需要自定义查询的方法，使用 MyBatis 注解（@Select 等）写 SQL，同样不依赖 XML 文件。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE username = #{username} AND del_flag = 0")
    SysUser selectByUsername(@Param("username") String username);
}
