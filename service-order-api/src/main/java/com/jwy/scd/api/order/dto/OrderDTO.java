package com.jwy.scd.api.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单对外 DTO。
 */
@Data
@Schema(name = "Order", description = "订单主信息（含明细）")
public class OrderDTO {

    @Schema(description = "订单主键 ID", example = "1")
    private Long id;

    @Schema(description = "业务订单号（对外可见，服务端生成：SO + yyyyMMdd + 雪花 ID）",
            example = "SO202609212097844166456669394")
    private String orderNo;

    @Schema(description = "下单用户 ID", example = "2")
    private Long userId;

    @Schema(description = "下单用户名（通过 Feign 从 service-system 取得后冗余存储）", example = "zhangsan")
    private String username;

    @Schema(description = "订单总金额（元）", example = "798.00")
    private BigDecimal totalAmount;

    @Schema(description = "订单状态：0=已创建，1=已完成，2=已取消", example = "0")
    private Integer status;

    @Schema(description = "订单状态描述", example = "已创建")
    private String statusDesc;

    @Schema(description = "订单明细列表")
    private List<OrderItemDTO> items;

    @Schema(description = "下单时间", example = "2026-09-20T15:30:12")
    private LocalDateTime createTime;
}
