package com.jwy.scd.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器。
 *
 * <p>实体上用 {@code @TableField(fill = FieldFill.INSERT)} 标注了 createTime / updateTime，
 * 但只有容器里存在一个 {@link MetaObjectHandler} 时填充才会真正发生——否则这两个字段会被当作 null
 * 而被 INSERT 语句忽略，只能靠数据库 DEFAULT CURRENT_TIMESTAMP 兜底，
 * 导致「插入后立刻返回的 DTO 里 createTime 是 null」。
 *
 * <p>这里统一在插入/更新前写入时间，让实体内存对象与数据库保持一致。
 */
@Component
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
