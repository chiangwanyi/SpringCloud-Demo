package com.jwy.scd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jwy.scd.api.dto.UserInfoDTO;
import com.jwy.scd.remote.SysUserRemoteService;
import com.jwy.scd.api.order.dto.OrderCreateDTO;
import com.jwy.scd.api.order.dto.OrderDTO;
import com.jwy.scd.api.order.dto.OrderItemCreateDTO;
import com.jwy.scd.api.order.dto.OrderItemDTO;
import com.jwy.scd.entity.BizOrder;
import com.jwy.scd.entity.BizOrderItem;
import com.jwy.scd.entity.BizProduct;
import com.jwy.scd.exception.OrderException;
import com.jwy.scd.mapper.BizOrderItemMapper;
import com.jwy.scd.mapper.BizOrderMapper;
import com.jwy.scd.mapper.BizProductMapper;
import com.jwy.scd.service.IOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl extends ServiceImpl<BizOrderMapper, BizOrder> implements IOrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 订单状态：已创建 */
    public static final int STATUS_CREATED = 0;
    /** 订单状态：已完成 */
    public static final int STATUS_FINISHED = 1;
    /** 订单状态：已取消 */
    public static final int STATUS_CANCELLED = 2;

    /**
     * ★ 跨服务调用组件（带 Sentinel 降级）。
     *
     * <p>{@link SysUserRemoteService} 内部注入 Feign 客户端（由启动类 {@code @EnableFeignClients}
     * 生成的 SysUserApi 代理），并用 {@code @SentinelResource + fallback} 声明了降级。之所以把
     * 远程调用抽成独立 Bean 而不是本类 private 方法，是因为 {@code @SentinelResource} 依赖 AOP
     * 拦截，private 方法 + 类内自调用都会让注解静默失效。通过注入这个组件调用，AOP 才能正确拦截。
     */
    private final SysUserRemoteService sysUserRemoteService;

    private final BizProductMapper productMapper;

    private final BizOrderItemMapper orderItemMapper;

    public OrderServiceImpl(SysUserRemoteService sysUserRemoteService,
                            BizProductMapper productMapper,
                            BizOrderItemMapper orderItemMapper) {
        this.sysUserRemoteService = sysUserRemoteService;
        this.productMapper = productMapper;
        this.orderItemMapper = orderItemMapper;
    }

    /**
     * 下单主流程。
     *
     * <p><b>关于事务边界的取舍</b>：这里把跨服务调用放在了 {@code @Transactional} 方法内部，
     * 好处是整段逻辑读起来是线性的，便于理解调用链；代价是这个本地事务会横跨一次网络请求，
     * 期间一直占用数据库连接（远程调用耗时越长，连接被占越久，高并发下容易成为瓶颈）。
     * 生产上的常见做法是「先远程校验 → 再开事务做本地写」，或把远程调用改成异步/消息。
     * 作为学习项目，这里保留在事务内，并在此明确标注这个坑。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDTO createOrder(OrderCreateDTO dto) {
        // ---------- 1. 基础参数校验 ----------
        if (dto == null || dto.getUserId() == null) {
            throw OrderException.badRequest("userId 不能为空");
        }
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw OrderException.badRequest("订单明细不能为空，至少购买一件商品");
        }

        // ---------- 2. ★ 跨服务调用：Feign 远程查询下单用户 ----------
        UserInfoDTO user = queryUserByFeign(dto.getUserId());
        if (user == null) {
            throw OrderException.badRequest("下单用户不存在：userId=" + dto.getUserId());
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw OrderException.badRequest("该用户已被禁用，无法下单：" + user.getUsername());
        }
        log.info("Feign 调用成功：userId={}, username={}, nickname={}",
                user.getId(), user.getUsername(), user.getNickname());

        // ---------- 3. 逐条处理明细：校验商品 → 扣库存 → 生成快照 ----------
        List<BizOrderItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItemCreateDTO itemDto : dto.getItems()) {
            if (itemDto.getProductId() == null) {
                throw OrderException.badRequest("明细中的 productId 不能为空");
            }
            int quantity = itemDto.getQuantity() == null ? 0 : itemDto.getQuantity();
            if (quantity <= 0) {
                throw OrderException.badRequest("购买数量必须大于 0，当前值：" + itemDto.getQuantity());
            }

            BizProduct product = productMapper.selectById(itemDto.getProductId());
            if (product == null) {
                throw OrderException.notFound("商品不存在：productId=" + itemDto.getProductId());
            }
            if (product.getStatus() == null || product.getStatus() != 1) {
                throw OrderException.badRequest("商品已下架：" + product.getName());
            }

            // 条件更新扣库存：SQL 自带 stock >= quantity 判断，返回 0 即库存不足。
            // 若这里抛异常，前面已扣减的库存会随本地事务一起回滚。
            int affected = productMapper.deductStock(product.getId(), quantity);
            if (affected == 0) {
                throw OrderException.badRequest(String.format(
                        "商品「%s」库存不足，当前库存 %d，本次需要 %d",
                        product.getName(), product.getStock(), quantity));
            }

            BizOrderItem item = new BizOrderItem();
            item.setProductId(product.getId());
            // 价格与名称取下单时刻的快照，客户端传来的值一律不信任
            item.setProductName(product.getName());
            item.setPrice(product.getPrice());
            item.setQuantity(quantity);
            BigDecimal amount = product.getPrice().multiply(BigDecimal.valueOf(quantity));
            item.setAmount(amount);
            items.add(item);

            totalAmount = totalAmount.add(amount);
        }

        // ---------- 4. 写订单主表 ----------
        BizOrder order = new BizOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(user.getId());
        // 冗余存一份用户名快照：查订单时不必再远程调用 service-system
        order.setUsername(user.getUsername());
        order.setTotalAmount(totalAmount);
        order.setStatus(STATUS_CREATED);
        save(order);

        // ---------- 5. 写明细 ----------
        for (BizOrderItem item : items) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        log.info("下单成功：orderNo={}, userId={}, 明细 {} 条, 总金额 {}",
                order.getOrderNo(), user.getId(), items.size(), totalAmount);
        return toDto(order, items);
    }

    @Override
    public OrderDTO getOrderDto(Long id) {
        BizOrder order = getById(id);
        if (order == null) {
            return null;
        }
        return toDto(order, orderItemMapper.selectByOrderId(order.getId()));
    }

    @Override
    public OrderDTO getOrderDtoByNo(String orderNo) {
        BizOrder order = baseMapper.selectByOrderNo(orderNo);
        if (order == null) {
            return null;
        }
        return toDto(order, orderItemMapper.selectByOrderId(order.getId()));
    }

    @Override
    public List<OrderDTO> listOrderDtos(Long userId) {
        LambdaQueryWrapper<BizOrder> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(BizOrder::getUserId, userId);
        }
        wrapper.orderByDesc(BizOrder::getId);
        List<BizOrder> orders = list(wrapper);
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }

        // 一次性把全部明细查出来再分组，避免逐条订单查询造成的 N+1
        List<Long> orderIds = orders.stream().map(BizOrder::getId).collect(Collectors.toList());
        Map<Long, List<BizOrderItem>> itemMap = orderItemMapper.selectByOrderIds(orderIds).stream()
                .collect(Collectors.groupingBy(BizOrderItem::getOrderId, LinkedHashMap::new, Collectors.toList()));

        return orders.stream()
                .map(o -> toDto(o, itemMap.getOrDefault(o.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDTO cancelOrder(Long id) {
        BizOrder order = getById(id);
        if (order == null) {
            throw OrderException.notFound("订单不存在：id=" + id);
        }
        if (STATUS_CANCELLED == order.getStatus()) {
            throw OrderException.badRequest("订单已是取消状态，无需重复取消：" + order.getOrderNo());
        }
        if (STATUS_FINISHED == order.getStatus()) {
            throw OrderException.badRequest("订单已完成，不能取消：" + order.getOrderNo());
        }

        List<BizOrderItem> items = orderItemMapper.selectByOrderId(order.getId());
        // 归还库存（与下单扣减对称，同样由本地事务保证要么全成要么全不成）
        for (BizOrderItem item : items) {
            productMapper.restoreStock(item.getProductId(), item.getQuantity());
        }

        order.setStatus(STATUS_CANCELLED);
        updateById(order);
        log.info("订单已取消并归还库存：orderNo={}, 明细 {} 条", order.getOrderNo(), items.size());
        return toDto(order, items);
    }

    @Override
    public boolean deleteOrder(Long id) {
        return removeById(id);
    }

    /**
     * 通过 Feign 调用 service-system 查询用户。
     *
     * <p>降级由 {@link SysUserRemoteService#getUserById} 上的 {@code @SentinelResource + fallback}
     * 完成：当下游 service-system 不可用 / 超时 / 熔断时，Sentinel 自动切入降级方法，抛出明确的
     * 业务异常，再由全局异常处理器转成 503 响应。因此这里无需再手写 try-catch。
     */
    private UserInfoDTO queryUserByFeign(Long userId) {
        return sysUserRemoteService.getUserById(userId);
    }

    /** 生成业务订单号：SO + 时间戳 + 3 位随机数（演示用，生产应保证全局唯一） */
    private String generateOrderNo() {
        return "SO" + LocalDateTime.now().format(ORDER_NO_FORMATTER)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }

    private String statusDesc(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case STATUS_CREATED:
                return "已创建";
            case STATUS_FINISHED:
                return "已完成";
            case STATUS_CANCELLED:
                return "已取消";
            default:
                return "未知";
        }
    }

    /** 实体 -> 对外 DTO（含明细） */
    private OrderDTO toDto(BizOrder order, List<BizOrderItem> items) {
        if (order == null) {
            return null;
        }
        OrderDTO dto = new OrderDTO();
        dto.setId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setUserId(order.getUserId());
        dto.setUsername(order.getUsername());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setStatus(order.getStatus());
        dto.setStatusDesc(statusDesc(order.getStatus()));
        dto.setCreateTime(order.getCreateTime());
        dto.setItems(items == null ? Collections.emptyList() : items.stream()
                .map(this::toItemDto)
                .collect(Collectors.toList()));
        return dto;
    }

    private OrderItemDTO toItemDto(BizOrderItem item) {
        OrderItemDTO dto = new OrderItemDTO();
        dto.setId(item.getId());
        dto.setOrderId(item.getOrderId());
        dto.setProductId(item.getProductId());
        dto.setProductName(item.getProductName());
        dto.setPrice(item.getPrice());
        dto.setQuantity(item.getQuantity());
        dto.setAmount(item.getAmount());
        return dto;
    }
}
