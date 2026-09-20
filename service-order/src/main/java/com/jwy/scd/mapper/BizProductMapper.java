package com.jwy.scd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jwy.scd.entity.BizProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品 Mapper。继承 {@link BaseMapper} 即获得通用 CRUD。
 */
@Mapper
public interface BizProductMapper extends BaseMapper<BizProduct> {

    /**
     * 扣减库存：把「库存是否足够」的判断和「扣减」放进同一条 SQL，
     * 由数据库的行锁保证原子性，天然防止并发超卖。
     *
     * <p>返回受影响行数：1 表示扣减成功，0 表示库存不足或商品不存在。
     * 若先 select 判断再 update，并发下会出现「两个请求都判断通过、结果超卖」的问题。
     */
    @Update("UPDATE biz_product SET stock = stock - #{quantity} "
            + "WHERE id = #{productId} AND del_flag = 0 AND stock >= #{quantity}")
    int deductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /** 归还库存（订单取消时把已扣减的数量加回去） */
    @Update("UPDATE biz_product SET stock = stock + #{quantity} WHERE id = #{productId}")
    int restoreStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
