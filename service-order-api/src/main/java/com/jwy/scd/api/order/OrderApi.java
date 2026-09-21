package com.jwy.scd.api.order;

import com.jwy.scd.api.order.dto.OrderCreateDTO;
import com.jwy.scd.api.order.dto.OrderDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * service-order 对外提供的订单服务契约。
 *
 * <p>该接口同时承担两个职责（与 {@code SysUserApi} 一致）：
 * <ol>
 *     <li>实现方（service-order 模块）用 {@code @RestController} 实现它，提供 HTTP 接口；</li>
 *     <li>调用方启用 {@code @EnableFeignClients} 后即可获得声明式远程调用。</li>
 * </ol>
 *
 * <p><b>路径写法</b>：前缀 {@code /api/order} 写在每个方法上，不用类级 {@code @RequestMapping}。
 * 这是 Spring Cloud OpenFeign 5.0.0 的硬性限制（{@code @FeignClient} 接口不允许类级
 * {@code @RequestMapping}），详见 {@code SysUserApi} 的类注释。
 *
 * <p><b>关于 contextId</b>：{@code OrderApi} 与 {@link ProductApi} 都指向同一个服务
 * {@code service-order}，即 {@code @FeignClient} 的 name 相同。Spring Cloud OpenFeign 默认用
 * name 作为 FeignClientSpecification 的 bean 名，两个同 name 的客户端会互相覆盖并导致启动失败，
 * 因此这里必须显式指定互不相同的 {@code contextId}。
 */
@FeignClient(name = "service-order", contextId = "orderApi")
@Tag(name = "订单管理", description = "下单、按 ID / 订单号查询、订单列表、取消与删除")
public interface OrderApi {

    @Operation(summary = "提交订单",
            description = "根据 userId 与商品明细创建订单。服务端会通过 Feign 调用 service-system "
                    + "校验下单用户是否存在且状态正常，随后回填商品快照、扣减库存并计算总金额。")
    @PostMapping("/api/order")
    OrderDTO createOrder(@RequestBody OrderCreateDTO dto);

    @Operation(summary = "按主键查询订单", description = "根据订单主键 id 返回订单详情（含明细）")
    @GetMapping("/api/order/{id}")
    OrderDTO getOrder(@Parameter(description = "订单主键 ID", example = "1") @PathVariable("id") Long id);

    @Operation(summary = "按订单号查询订单", description = "根据业务订单号 orderNo 返回订单详情")
    @GetMapping("/api/order/by-no")
    OrderDTO getOrderByNo(@Parameter(description = "业务订单号", example = "SO2026092112345")
                          @RequestParam("orderNo") String orderNo);

    @Operation(summary = "查询订单列表",
            description = "不传 userId 时返回全部订单；传 userId 时只返回该用户的订单")
    @GetMapping("/api/order/list")
    List<OrderDTO> listOrders(@Parameter(description = "下单用户 ID，可选", example = "2")
                              @RequestParam(value = "userId", required = false) Long userId);

    @Operation(summary = "取消订单", description = "将订单状态置为已取消（2），并把已扣减的库存归还")
    @PostMapping("/api/order/{id}/cancel")
    OrderDTO cancelOrder(@Parameter(description = "订单主键 ID", example = "1") @PathVariable("id") Long id);

    @Operation(summary = "删除订单", description = "按主键逻辑删除订单（del_flag = 1），不归还库存")
    @DeleteMapping("/api/order/{id}")
    void deleteOrder(@Parameter(description = "订单主键 ID", example = "1") @PathVariable("id") Long id);
}
