package com.jwy.scd.api.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 下单时的单条明细入参（只描述「买什么、买几件」，
 * 商品名称与单价由服务端根据商品库回填，客户端不可信）。
 */
@Data
@Schema(name = "OrderItemCreate", description = "下单明细入参")
public class OrderItemCreateDTO {

    @Schema(description = "商品 ID", example = "1")
    private Long productId;

    @Schema(description = "购买数量", example = "2")
    private Integer quantity;
}
