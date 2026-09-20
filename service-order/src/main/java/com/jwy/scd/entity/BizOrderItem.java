package com.jwy.scd.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细实体。
 *
 * <p>productName 与 price 存的是「下单时刻的快照」，而不是外键实时关联：
 * 商品日后改名或调价，历史订单金额必须保持不变，否则对账就乱了。
 */
@Data
@TableName("biz_order_item")
public class BizOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属订单 ID（biz_order.id） */
    private Long orderId;

    private Long productId;

    /** 商品名称快照 */
    private String productName;

    /** 成交单价快照 */
    private BigDecimal price;

    private Integer quantity;

    /** 小计 = price × quantity */
    private BigDecimal amount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
