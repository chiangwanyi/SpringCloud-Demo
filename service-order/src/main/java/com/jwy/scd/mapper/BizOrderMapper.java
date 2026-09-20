package com.jwy.scd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jwy.scd.entity.BizOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 订单主表 Mapper。
 */
@Mapper
public interface BizOrderMapper extends BaseMapper<BizOrder> {

    /** 按业务订单号查询（订单号带唯一索引，走索引查询） */
    @Select("SELECT * FROM biz_order WHERE order_no = #{orderNo} AND del_flag = 0")
    BizOrder selectByOrderNo(@Param("orderNo") String orderNo);
}
