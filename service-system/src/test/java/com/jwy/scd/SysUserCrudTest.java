package com.jwy.scd;

import com.jwy.scd.entity.SysUser;
import com.jwy.scd.service.ISysUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * service-system 用户模块 CRUD 验证。
 * 使用 H2 内存库（src/main/resources/schema.sql 建表），覆盖 MyBatis-Plus 通用能力与自定义注解 Mapper。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SysUserCrudTest {

    @Autowired
    private ISysUserService userService;

    @Test
    void testCrud() {
        // 增：MyBatis-Plus 通用 save
        SysUser user = new SysUser();
        user.setUsername("zhangsan");
        user.setPassword("123456");
        user.setNickname("张三");
        user.setStatus(1);
        assertTrue(userService.save(user));
        assertNotNull(user.getId());

        // 查：IService.getById
        SysUser found = userService.getById(user.getId());
        assertNotNull(found);
        assertEquals("zhangsan", found.getUsername());

        // 自定义注解 Mapper 查询（@Select）
        SysUser byName = userService.getByUsername("zhangsan");
        assertNotNull(byName);
        assertEquals(user.getId(), byName.getId());

        // 改：IService.updateById
        found.setNickname("张三丰");
        userService.updateById(found);
        assertEquals("张三丰", userService.getById(user.getId()).getNickname());

        // 列表
        List<SysUser> list = userService.list();
        assertFalse(list.isEmpty());

        // 对外 DTO（脱敏，不含密码）；不存在的用户返回 null
        assertNotNull(userService.getUserInfo(user.getId()));
        assertNull(userService.getUserInfo(999999L));

        // 删：逻辑删除（del_flag = 1），删除后查询返回 null
        userService.removeById(user.getId());
        assertNull(userService.getById(user.getId()));
    }
}
