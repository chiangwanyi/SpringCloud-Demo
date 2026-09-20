package com.jwy.scd.api.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品新增 / 修改入参。
 */
@Data
@Schema(name = "ProductSave", description = "商品写入参数（新增或修改）")
public class ProductSaveDTO {

    @Schema(description = "商品名称", example = "机械键盘")
    private String name;

    @Schema(description = "单价（元）", example = "399.00")
    private BigDecimal price;

    @Schema(description = "库存数量", example = "100")
    private Integer stock;

    @Schema(description = "状态：1=上架，0=下架", example = "1")
    private Integer status;
}
