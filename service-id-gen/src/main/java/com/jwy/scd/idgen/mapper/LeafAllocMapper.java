package com.jwy.scd.idgen.mapper;

import com.jwy.scd.idgen.entity.LeafAlloc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 号段账本 Mapper。
 *
 * <p>刻意<b>不继承 {@code BaseMapper}</b>：这张表的主键是业务语义的 {@code biz_tag}（自然主键），
 * 既没有自增 id 也不需要逻辑删除，用不上通用 CRUD。只暴露三条精确的 SQL，
 * 让「发号服务对数据库做了什么」一眼可查——这比一个万能的 BaseMapper 更安全。
 */
@Mapper
public interface LeafAllocMapper {

    /**
     * 把某个 bizTag 的 {@code max_id} 整体推进一个 step，并返回受影响行数。
     *
     * <p>这一步是<b>整条发号链路唯一的并发仲裁点</b>：InnoDB 会对这一行加 X 锁，
     * 多个实例同时执行时会被串行化，各自拿到互不重叠的区间。
     *
     * <p>返回 0 表示这个 bizTag 不存在（没有种子行），调用方必须报错而不是返回空段。
     */
    @Update("UPDATE leaf_alloc SET max_id = max_id + `step` WHERE biz_tag = #{bizTag}")
    int bumpMaxId(@Param("bizTag") String bizTag);

    /**
     * 读回推进后的 {@code max_id}。
     *
     * <p><b>必须与 {@link #bumpMaxId} 在同一个事务里</b>——
     * 上面的 UPDATE 已经把行锁住且不提交就不释放，所以这里的 SELECT 读到的
     * 一定是自己刚写进去的值，而不是别人的。这条性质是防重号的关键。
     */
    @Select("SELECT biz_tag, max_id, `step`, description FROM leaf_alloc WHERE biz_tag = #{bizTag}")
    LeafAlloc selectByTag(@Param("bizTag") String bizTag);

    /** 全部 bizTag，用于服务启动时预热号段（对应 Leaf 的 {@code getAllTags}） */
    @Select("SELECT biz_tag FROM leaf_alloc ORDER BY biz_tag")
    List<String> selectAllTags();
}
