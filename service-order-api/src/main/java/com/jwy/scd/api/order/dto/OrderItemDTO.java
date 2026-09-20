package com.jwy.scd.api.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细对外 DTO（下单成功后回填的商品快照）。
 */
@Data
@Schema(name = "OrderItem", description = "订单明细")
public class OrderItemDTO {

    @Schema(description = "明细主键 ID", example = "1")
    private Long id;

    @Schema(description = "所属订单 ID", example = "1")
    private Long orderId;

    @Schema(description = "商品 ID", example = "1")
    private Long productId;

    @Schema(description = "商品名称快照（下单时刻的名称）", example = "机械键盘")
    private String productName;

    @Schema(description = "成交单价快照（下单时刻的价格）", example = "399.00")
    private BigDecimal price;

    @Schema(description = "购买数量", example = "2")
    private Integer quantity;

    @Schema(description = "小计金额 = price × quantity", example = "798.00")
    private BigDecimal amount;
}
