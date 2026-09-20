package com.jwy.scd.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体。
 *
 * <p>说明：商品与库存本应属于独立的商品服务，此处为教学演示暂时放在订单库中，
 * 使「扣减库存 + 写入订单」处于同一个本地事务内，无需分布式事务即可保证一致。
 * 后续学习 Seata 时，可把本表迁到 service-product，届时扣库存就变成跨服务调用。
 */
@Data
@TableName("biz_product")
public class BizProduct {

    /** 主键，与 MySQL 的 AUTO_INCREMENT 对应 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 单价，用 BigDecimal 避免浮点误差 */
    private BigDecimal price;

    private Integer stock;

    /** 状态：1=上架，0=下架 */
    private Integer status;

    @TableLogic
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
