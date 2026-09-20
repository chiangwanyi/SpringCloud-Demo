package com.jwy.scd.api.auth.session;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录会话（Session）数据模型 —— <b>service-auth 与 service-gateway 之间的「跨进程共享状态」契约</b>。
 *
 * <p><b>为什么需要这个类？</b>
 * 本项目采用「Redis Session Token」鉴权方案（而不是 JWT 无状态令牌）：
 * <ul>
 *     <li><b>写入方</b>：service-auth 在登录成功后生成令牌，把本对象序列化成 JSON 写入 Redis；</li>
 *     <li><b>读取方</b>：service-gateway 的鉴权过滤器直接读 Redis，拿到本对象即视为「令牌有效」，
 *         并从里面取出 {@code userId} / {@code username} 注入请求头，透传给下游业务服务；</li>
 *     <li><b>注销</b>：删除 Redis 中的 key，令牌立即失效（这是 Redis 方案相对 JWT 的最大优势：可即时吊销）。</li>
 * </ul>
 *
 * <p><b>这属于「共享契约」，必须放在 api 模块</b>：两个进程都要依赖同一份类定义，
 * 否则 JSON 字段名一旦不一致就会静默解析失败。Redis 的 key 规则见 {@link AuthSessionKeys}。
 *
 * <p><b>⚠️ 序列化约定（重要）</b>：
 * <ol>
 *     <li>本对象以 <b>JSON 字符串</b> 形式存放在 Redis 的 String 类型里（不是 Hash），
 *         这样用 {@code redis-cli GET} 就能直接肉眼查看，排障最简单；</li>
 *     <li>时间字段刻意用 {@code Long}（epoch 毫秒）而 <b>不用 {@code LocalDateTime}</b>：
 *         JSON 里日期类型的序列化结果依赖双方 Jackson 的 JavaTimeModule / 时区 / 格式配置，
 *         一旦有一侧配置不同就会反序列化失败，是跨服务契约的经典坑。
 *         用 epoch 毫秒则只依赖最基础的 JSON 数字类型，零配置风险。</li>
 * </ol>
 *
 * <p><b>扩展提示</b>：如果以后要做「基于角色的权限控制（RBAC）」，在这里加
 * {@code List<String> roles} / {@code List<String> permissions} 字段即可 ——
 * 它们会被 service-auth 在登录时从 service-system 查到并写入会话，
 * 网关读出来后可注入给下游，下游就不必再查一次库。这正是 Session Token
 * 相比「只返回布尔的校验接口」的价值所在。
 */
@Data
@Schema(name = "AuthSession", description = "登录会话（存于 Redis，由 service-auth 写入、网关读取）")
public class AuthSession {

    @Schema(description = "令牌字符串（同时也是 Redis key 的组成部分）", example = "3f2a1b9c8d7e6f5a4b3c2d1e0f9a8b7c")
    private String token;

    @Schema(description = "用户主键 ID（来自 service-system 的用户中心）", example = "2")
    private Long userId;

    @Schema(description = "登录用户名", example = "zhangsan")
    private String username;

    @Schema(description = "用户昵称 / 展示名", example = "张三")
    private String nickname;

    /**
     * 登录时间（epoch 毫秒）。
     *
     * <p>刻意用 Long 而不是 LocalDateTime，原因见类注释的「序列化约定」第 2 条。
     */
    @Schema(description = "登录时间（epoch 毫秒）", example = "1789000000000")
    private Long loginTime;
}
