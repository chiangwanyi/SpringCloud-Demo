package com.jwy.scd.api.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品对外 DTO。
 */
@Data
@Schema(name = "Product", description = "商品信息（含价格与库存）")
public class ProductDTO {

    @Schema(description = "商品主键 ID", example = "1")
    private Long id;

    @Schema(description = "商品名称", example = "机械键盘")
    private String name;

    @Schema(description = "单价（元）", example = "399.00")
    private BigDecimal price;

    @Schema(description = "剩余库存数量", example = "100")
    private Integer stock;

    @Schema(description = "状态：1=上架，0=下架", example = "1")
    private Integer status;

    @Schema(description = "创建时间", example = "2026-09-20T10:00:00")
    private LocalDateTime createTime;
}
