package com.jwy.scd.controller;

import com.jwy.scd.api.order.OrderApi;
import com.jwy.scd.api.order.dto.OrderCreateDTO;
import com.jwy.scd.api.order.dto.OrderDTO;
import com.jwy.scd.service.IOrderService;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单接口实现：实现 service-order-api 中定义的 {@link OrderApi} 契约。
 * <p>HTTP 映射（/api/order/*）继承自接口上的 {@code @RequestMapping}，
 * 这里只负责把请求转给服务层，业务逻辑（含跨服务调用）都在
 * {@code OrderServiceImpl} 中。
 */
@RestController
public class OrderController implements OrderApi {

    private final IOrderService orderService;

    public OrderController(IOrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public OrderDTO createOrder(OrderCreateDTO dto) {
        return orderService.createOrder(dto);
    }

    @Override
    public OrderDTO getOrder(Long id) {
        return orderService.getOrderDto(id);
    }

    @Override
    public OrderDTO getOrderByNo(String orderNo) {
        return orderService.getOrderDtoByNo(orderNo);
    }

    @Override
    public List<OrderDTO> listOrders(Long userId) {
        return orderService.listOrderDtos(userId);
    }

    @Override
    public OrderDTO cancelOrder(Long id) {
        return orderService.cancelOrder(id);
    }

    @Override
    public void deleteOrder(Long id) {
        orderService.deleteOrder(id);
    }
}
