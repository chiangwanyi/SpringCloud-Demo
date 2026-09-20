package com.jwy.scd.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表实体。
 *
 * <p><b>关于 username 冗余</b>：该字段在 service-system 的 sys_user 中才是权威数据，
 * 这里刻意存一份快照。这不是设计失误，而是微服务里常见的取舍：
 * <ul>
 *     <li>查询订单列表时不必再远程调用 service-system（避免 N+1 次跨服务请求）；</li>
 *     <li>用户改名后历史订单仍保留下单时的名称，符合业务语义；</li>
 *     <li>代价是存在短暂的数据不一致窗口，需靠事件或定时任务补偿。</li>
 * </ul>
 */
@Data
@TableName("biz_order")
public class BizOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务订单号，对外可见，由服务端生成 */
    private String orderNo;

    /** 下单用户 ID（对应 sys_user.id） */
    private Long userId;

    /** 下单用户名快照（通过 Feign 从 service-system 取得） */
    private String username;

    /** 订单总金额 = 各明细小计之和 */
    private BigDecimal totalAmount;

    /** 状态：0=已创建，1=已完成，2=已取消 */
    private Integer status;

    @TableLogic
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
