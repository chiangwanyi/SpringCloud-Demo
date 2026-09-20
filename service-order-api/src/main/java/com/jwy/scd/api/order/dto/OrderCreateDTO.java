package com.jwy.scd.api.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 下单入参。
 *
 * <p>注意这里只需要 {@code userId}，不需要传 username：
 * 用户名由 service-order 通过 Feign 调用 service-system 查询得到，
 * 既避免客户端伪造，也顺带成为「跨服务调用」的第一个真实场景。
 */
@Data
@Schema(name = "OrderCreate", description = "下单入参：下单人 + 商品明细")
public class OrderCreateDTO {

    @Schema(description = "下单用户 ID（对应 service-system 的 sys_user.id）", example = "2")
    private Long userId;

    @Schema(description = "购买明细列表，至少一条")
    private List<OrderItemCreateDTO> items;
}
