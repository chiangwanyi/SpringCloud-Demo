package com.jwy.scd.service;

import com.jwy.scd.api.auth.dto.TokenInfoDTO;
import com.jwy.scd.api.auth.session.AuthSession;
import com.jwy.scd.api.auth.session.AuthSessionKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;

/**
 * 会话令牌服务（Redis Session Token 方案的「写入方」）。
 *
 * <p><b>核心设计：以 Redis 为唯一状态源，网关直接读，不再提供任何校验接口。</b>
 *
 * <p>本服务只做三件事：
 * <ol>
 *     <li>{@link #issue} 登录成功后签发令牌，把 {@link AuthSession} 以 JSON 写入 Redis 并设置 TTL；</li>
 *     <li>（会话校验不在这里 —— 校验由 service-gateway 直接读 Redis 完成，见 {@link AuthSessionKeys}）；</li>
 *     <li>{@link #revoke} 注销时删除 Redis key，令牌立即失效。</li>
 * </ol>
 *
 * <p><b>为什么用 {@link StringRedisTemplate} 而不是 {@code RedisTemplate&lt;Object,Object&gt;}？</b>
 * 相比默认的 JDK 序列化，String 模板配合 Jackson 写出的值是可读 JSON，
 * {@code redis-cli GET} 能直接看懂，排障成本低得多；而且值格式对客户端透明，
 * 将来别的语言写的服务也能读。这与 {@link AuthSession} 类注释里的「序列化约定」相呼应。
 *
 * <p><b>关于 TTL</b>：本次不做服务端续期（滑动过期）—— 续期由网关在读会话时按需 {@code EXPIRE} 完成，
 * 见网关的 TokenAuthFilter。这样认证服务保持「只在登录/注销时被调用」的极低频特征。
 *
 * <p><b>⚠️ 注意 Jackson 的包名是 {@code tools.jackson.*} 而不是 {@code com.fasterxml.jackson.*}</b>：
 * Spring Boot 4 已把默认 JSON 库从 Jackson 2 升级到 <b>Jackson 3</b>，
 * 自动配置提供的 {@code ObjectMapper} bean 类型是 {@code tools.jackson.databind.ObjectMapper}。
 * 写老包名会在启动时报「No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper'」。
 * 另一个变化：Jackson 3 的 {@code JacksonException} 继承自 {@code RuntimeException}（不再是受检异常），
 * 所以这里 catch 它不需要 throws 声明。
 */
@Service
public class SessionTokenService {

    private static final Logger log = LoggerFactory.getLogger(SessionTokenService.class);

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    /** 会话有效期（秒）。可通过 application.yml 的 app.auth.session-timeout 调整。 */
    private final long sessionTimeoutSeconds;

    public SessionTokenService(StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper,
                               @Value("${app.auth.session-timeout:1800}") long sessionTimeoutSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.sessionTimeoutSeconds = sessionTimeoutSeconds;
    }

    /**
     * 签发令牌：生成随机令牌 → 组装会话 → 写入 Redis（带 TTL）→ 返回给前端。
     *
     * <p>令牌用 {@code UUID} 去掉中划线（32 位十六进制）。它是<b>不透明令牌（opaque token）</b>：
     * 本身不含任何信息，一切以 Redis 中的会话为准 —— 这正是「可以即时吊销」的原因
     * （对比 JWT：签发后就无法撤回，只能等过期，除非再维护一份黑名单）。
     *
     * @param userId   用户主键（来自用户中心 service-system）
     * @param username 登录用户名
     * @param nickname 用户昵称
     * @return 返回给前端的令牌信息
     */
    public TokenInfoDTO issue(Long userId, String username, String nickname) {
        String token = UUID.randomUUID().toString().replace("-", "");

        AuthSession session = new AuthSession();
        session.setToken(token);
        session.setUserId(userId);
        session.setUsername(username);
        session.setNickname(nickname);
        session.setLoginTime(System.currentTimeMillis());

        String key = AuthSessionKeys.sessionKey(token);
        redisTemplate.opsForValue().set(key, toJson(session), Duration.ofSeconds(sessionTimeoutSeconds));
        log.info("签发会话令牌：username={}, userId={}, key={}, ttl={}s", username, userId, key, sessionTimeoutSeconds);

        TokenInfoDTO info = new TokenInfoDTO();
        info.setToken(token);
        info.setTokenType("Bearer");
        info.setExpiresIn(sessionTimeoutSeconds);
        info.setUserId(userId);
        info.setUsername(username);
        info.setNickname(nickname);
        return info;
    }

    /**
     * 注销令牌：删除 Redis 会话。
     *
     * <p>用 {@code DEL} 而不是「标记为失效」：Redis 里 key 是否存在就是会话是否有效的唯一判据，
     * 删除后网关下一次读取就查不到，立即 401。删除不存在的 key 也不会报错，天然幂等，
     * 所以重复调用注销接口是安全的。
     */
    public void revoke(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        String key = AuthSessionKeys.sessionKey(token);
        Boolean removed = redisTemplate.delete(key);
        log.info("注销会话令牌：key={}, 是否命中={}", key, removed);
    }

    /** 会话对象 → JSON；理论上不会失败（字段都是基础类型），失败则抛出便于尽早暴露问题 */
    private String toJson(AuthSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (JacksonException ex) {
            // 走到这里说明 AuthSession 被改成了无法序列化的类型，属于编码缺陷，直接抛出让登录失败
            throw new IllegalStateException("会话序列化失败：" + session, ex);
        }
    }
}
