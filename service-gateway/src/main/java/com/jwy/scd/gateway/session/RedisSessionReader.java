package com.jwy.scd.gateway.session;

import com.jwy.scd.api.auth.session.AuthSession;
import com.jwy.scd.api.auth.session.AuthSessionKeys;
import com.jwy.scd.gateway.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * 网关侧的会话读取器 —— <b>整个 Redis Session Token 方案的核心</b>。
 *
 * <p>它替代了原先「Feign 远程调用认证服务的校验接口」那一跳：
 * <pre>
 *   旧：网关 --HTTP--> service-auth --查内存--> 返回 true/false      （每个请求 +1 RPC，auth 挂则全站挂）
 *   新：网关 --读 Redis--> 拿到 AuthSession                          （0 次 RPC，auth 不参与请求链路）
 * </pre>
 * 「令牌是否有效」被简化成一个 Redis 查询：<b>key 存在且能解析出会话 = 有效</b>。
 *
 * <p><b>设计取舍说明</b>：
 * <ul>
 *     <li><b>不做本地缓存</b>：本地缓存会话会引入「用户已注销但网关仍放行」的窗口期，
 *         而这正是选 Redis 方案（而非 JWT）的唯一理由 —— 即时吊销。要不要牺牲正确性换性能，
 *         应该在压测数据出来后决定，而不是提前假设（真要做也应配合「注销时广播失效」）；
 *     <li><b>Redis 异常必须向上抛</b>：这里刻意不 catch 连接类异常，让调用方（过滤器）
 *         能把「Redis 不可用」和「会话不存在」区分开 —— 前者应回 503，后者才是 401。
 *         把两者混成 401 是常见事故：Redis 抖动时所有用户被莫名踢下线；</li>
 *     <li><b>解析失败按「无效」处理但不抛异常</b>：JSON 解析失败说明写入方与读取方的契约漂移了
 *         （见 {@link AuthSession} 的序列化约定）。这时记 error 日志 + 拒绝放行，
 *         属于「安全的失败方向」。</li>
 * </ul>
 */
@Component
public class RedisSessionReader {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionReader.class);

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final AuthProperties authProperties;

    public RedisSessionReader(StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              AuthProperties authProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.authProperties = authProperties;
    }

    /**
     * 按令牌读取会话。
     *
     * @param token 令牌本体（已由调用方从 Authorization 头解析出来，不含 "Bearer " 前缀）
     * @return 会话对象；令牌不存在或已过期时返回 {@code null}
     * @throws org.springframework.dao.DataAccessException Redis 不可用（由调用方决定回 503）
     */
    public AuthSession find(String token) {
        String key = AuthSessionKeys.sessionKey(token);

        // GET 是 O(1) 操作；key 不存在或已过 TTL 自动删除时返回 null，正好等价于「令牌无效」
        String json = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(json)) {
            return null;
        }

        AuthSession session;
        try {
            session = objectMapper.readValue(json, AuthSession.class);
        } catch (JacksonException ex) {
            log.error("会话反序列化失败，可能是契约漂移（写入方与读取方的 AuthSession 定义不一致）：key={}, value={}",
                    key, json, ex);
            return null;
        }

        if (authProperties.isRefreshOnAccess()) {
            // 滑动过期：用户持续活动就不掉线。
            // 代价是每个请求多一次写命令（EXPIRE），若压测证明有影响，可改为
            // 「仅在剩余 TTL 低于阈值时续期」或换成一条 Lua 脚本把 GET+EXPIRE 合成单次往返。
            redisTemplate.expire(key, Duration.ofSeconds(authProperties.getSessionTimeout()));
        }
        return session;
    }
}
