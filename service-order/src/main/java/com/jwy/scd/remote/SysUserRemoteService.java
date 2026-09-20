package com.jwy.scd.remote;

import com.jwy.scd.api.SysUserApi;
import com.jwy.scd.api.dto.UserInfoDTO;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 对 service-system 的跨服务调用封装，作为 Sentinel 保护的「受保护资源」载体。
 *
 * <p><b>为什么要把跨服务调用单独抽成一个 Bean，而不是放在 OrderServiceImpl 的 private 方法里？</b>
 * {@code @SentinelResource} 依赖 Spring AOP 拦截，而 AOP 有两个前提：
 * <ol>
 *   <li>方法必须是 <strong>public</strong>（private 方法 CGLIB 代理覆盖不了）；</li>
 *   <li>必须通过 <strong>Spring 代理对象</strong>调用（类内 {@code this.xxx()} 自调用会绕过代理）。</li>
 * </ol>
 * 把远程调用抽成一个独立的 {@link Component}，由其它 Bean 注入后调用，两个前提就都满足了——
 * 这是 {@code @SentinelResource} 最经典、最可靠的用法。
 */
@Component
public class SysUserRemoteService {

    private static final Logger log = LoggerFactory.getLogger(SysUserRemoteService.class);

    private final SysUserApi sysUserApi;

    public SysUserRemoteService(SysUserApi sysUserApi) {
        this.sysUserApi = sysUserApi;
    }

    /**
     * 查询用户（Sentinel 受保护资源）。
     *
     * <p>{@code @SentinelResource} 的关键参数：
     * <ul>
     *   <li>{@code value}：资源名，Sentinel 规则（限流/熔断）按这个名字配置。</li>
     *   <li>{@code fallback}：降级方法，处理「业务异常」（下游调用失败、方法内部抛异常）。</li>
     *   <li>{@code blockHandler}：被 Sentinel 规则<strong>主动拦截</strong>（限流、熔断规则拒绝）时
     *       调用的方法，对应 {@link BlockException}。</li>
     * </ul>
     *
     * <p><b>两者区别（易混，务必分清）</b>：{@code blockHandler} 管「被规则挡掉」，
     * {@code fallback} 管「业务本身出错」。限流触发的是 {@link BlockException} → 走 blockHandler；
     * 下游挂了抛的是业务异常 → 走 fallback。
     *
     * @param userId 用户主键
     * @return 用户信息
     */
    @SentinelResource(value = "getUserById",
            fallback = "getUserByIdFallback",
            blockHandler = "getUserByIdBlockHandler")
    public UserInfoDTO getUserById(Long userId) {
        return sysUserApi.getUserById(userId);
    }

    /**
     * {@code getUserById} 的降级方法。签名 = 原方法参数 + {@link Throwable}，返回类型一致。
     * 下游 service-system 不可用/超时/熔断时执行，抛出明确的业务异常由全局异常处理器转成 503。
     */
    public UserInfoDTO getUserByIdFallback(Long userId, Throwable ex) {
        log.error("用户服务降级：service-system.getUserById(userId={}) 不可用", userId, ex);
        throw new RuntimeException("调用用户服务(service-system)失败，请稍后重试", ex);
    }

    /**
     * {@code getUserById} 被限流/熔断规则拦截时执行。签名 = 原方法参数 + {@link BlockException}。
     * 这里抛出一个明确的「请求被限流」业务信号，与「服务不可用」的降级区分开。
     */
    public UserInfoDTO getUserByIdBlockHandler(Long userId, BlockException ex) {
        log.warn("用户服务调用被限流：userId={}", userId);
        throw new RuntimeException("请求过于频繁，请稍后重试", ex);
    }
}
