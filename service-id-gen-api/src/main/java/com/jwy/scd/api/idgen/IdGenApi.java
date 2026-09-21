package com.jwy.scd.api.idgen;

import com.jwy.scd.api.idgen.dto.IdGenStatusDTO;
import com.jwy.scd.api.idgen.dto.SegmentDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * service-id-gen 对外提供的发号服务契约。
 *
 * <p>该接口同时承担两个职责（与本项目其它 api 模块一致）：
 * <ol>
 *     <li>实现方（service-id-gen 模块）用 {@code @RestController} 实现它，提供 HTTP 接口；</li>
 *     <li>调用方（service-order）在启动类上加 {@code @EnableFeignClients(clients = IdGenApi.class)}
 *         即可把它注册成 Feign 客户端。</li>
 * </ol>
 *
 * <p><b>⚠️ 带 {@code @FeignClient} 的接口上不允许出现类级 {@code @RequestMapping}</b>——
 * OpenFeign 5.0 的 {@code SpringMvcContract} 会直接抛
 * {@code IllegalArgumentException: @RequestMapping annotation not allowed on @FeignClient interfaces}。
 * 路径一律写在方法级映射注解里。
 *
 * <p><b>提供两个取号接口，面向两种不同的接入方式：</b>
 *
 * <table border="1">
 *     <caption>两种接入方式对比</caption>
 *     <tr><th>接口</th><th>形态</th><th>每次发号的网络开销</th><th>适用</th></tr>
 *     <tr>
 *         <td>{@link #getSegment}</td>
 *         <td><b>号段下放</b>：一次拿一段，业务本地发号</td>
 *         <td>0（分摊到 step 次发号上）</td>
 *         <td>高频业务，本项目 service-order 走这条</td>
 *     </tr>
 *     <tr>
 *         <td>{@link #nextId}</td>
 *         <td><b>单发</b>：服务端自己持段，一次给一个</td>
 *         <td>1 次 HTTP 往返</td>
 *         <td>低频调用、手工调试、对照实验</td>
 *     </tr>
 * </table>
 *
 * <p>注意 {@code /api/id/segment} 是<b>无状态</b>的：它每次直接向 MySQL 原子取一段就返回，
 * 服务端不缓存。这样 service-id-gen 可以随意横向扩容，DB 是唯一的冲突仲裁者。
 * 双 buffer 属于「持有号段的那一侧」——也就是业务服务。
 */
@FeignClient(name = "service-id-gen")
@Tag(name = "分布式发号", description = "Leaf-segment 号段模式发号服务")
public interface IdGenApi {

    /**
     * 领取一个<b>新号段</b>（号段下放，推荐）。
     *
     * <p>返回的区间是调用方独占的，可以放心在本地把它发完。
     */
    @Operation(summary = "领取一个号段",
            description = "向 MySQL 原子取一段连续 ID 并独占返回；服务端无状态，可直接横向扩容")
    @GetMapping("/api/id/segment")
    SegmentDTO getSegment(@Parameter(description = "业务标识", example = "order")
                          @RequestParam("bizTag") String bizTag);

    /**
     * 领取<b>单个 ID</b>（服务端用自己的双 buffer 发号，适合低频场景与调试）。
     *
     * <p>失败（段尽且取不到新段）时返回 503，<b>不会</b>降级成本地自增。
     */
    @Operation(summary = "领取单个 ID",
            description = "由服务端持有的双 buffer 发号；低频或调试用，高频业务请用 /api/id/segment")
    @GetMapping("/api/id/next")
    long nextId(@Parameter(description = "业务标识", example = "order")
                @RequestParam("bizTag") String bizTag);

    @Operation(summary = "查询全部 bizTag 的号段状态",
            description = "用于观察双 buffer：current 区间、剩余量、预备段是否就绪")
    @GetMapping("/api/id/status")
    List<IdGenStatusDTO> status();
}
