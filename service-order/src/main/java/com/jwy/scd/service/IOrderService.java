package com.jwy.scd.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jwy.scd.api.order.dto.OrderCreateDTO;
import com.jwy.scd.api.order.dto.OrderDTO;
import com.jwy.scd.entity.BizOrder;

import java.util.List;

/**
 * 订单服务接口。
 */
public interface IOrderService extends IService<BizOrder> {

    /** 下单：内部会通过 Feign 调用 service-system 校验用户 */
    OrderDTO createOrder(OrderCreateDTO dto);

    OrderDTO getOrderDto(Long id);

    OrderDTO getOrderDtoByNo(String orderNo);

    /** 查询订单列表；userId 为 null 时返回全部 */
    List<OrderDTO> listOrderDtos(Long userId);

    /** 取消订单，并归还已扣减的库存 */
    OrderDTO cancelOrder(Long id);

    boolean deleteOrder(Long id);
}
