package com.jwy.scd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jwy.scd.entity.BizOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单明细 Mapper。
 */
@Mapper
public interface BizOrderItemMapper extends BaseMapper<BizOrderItem> {

    /** 按订单 ID 查询明细列表 */
    @Select("SELECT * FROM biz_order_item WHERE order_id = #{orderId} ORDER BY id")
    List<BizOrderItem> selectByOrderId(@Param("orderId") Long orderId);

    /** 批量查询多个订单的明细（用于订单列表，避免逐条查询造成 N+1） */
    @Select("<script>SELECT * FROM biz_order_item WHERE order_id IN "
            + "<foreach collection='orderIds' item='oid' open='(' separator=',' close=')'>#{oid}</foreach> "
            + "ORDER BY id</script>")
    List<BizOrderItem> selectByOrderIds(@Param("orderIds") List<Long> orderIds);
}
