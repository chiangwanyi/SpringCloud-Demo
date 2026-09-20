package com.jwy.scd;

import com.jwy.scd.api.auth.dto.TokenInfoDTO;
import com.jwy.scd.api.auth.session.AuthSession;
import com.jwy.scd.api.auth.session.AuthSessionKeys;
import com.jwy.scd.service.SessionTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SessionTokenService} 单元测试：验证「写入 Redis 的 key 与 value 格式」这一跨进程契约。
 *
 * <p>Redis 本身被 mock 掉（真实读写由端到端验证覆盖），重点是把<b>写进 Redis 的东西</b>抓出来断言：
 * <ul>
 *     <li>key 必须是 {@code auth:session:<token>} —— 网关靠这个规则查找，写错就是「登录成功但全部 401」；</li>
 *     <li>value 必须是能反序列化回 {@link AuthSession} 的 JSON，且 userId/nickname 都在；</li>
 *     <li>必须带 TTL（否则令牌永不过期，是严重安全问题）；</li>
 *     <li>注销必须是 DEL。</li>
 * </ul>
 */
class SessionTokenServiceTest {

    private static final long TIMEOUT_SECONDS = 1800L;

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final SessionTokenService service =
            new SessionTokenService(redisTemplate, objectMapper, TIMEOUT_SECONDS);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void issueWritesSessionJsonWithTtlUnderExpectedKey() throws Exception {
        TokenInfoDTO info = service.issue(2L, "zhangsan", "张三");

        assertNotNull(info.getToken());
        assertEquals("Bearer", info.getTokenType());
        assertEquals(TIMEOUT_SECONDS, info.getExpiresIn());
        assertEquals(2L, info.getUserId());
        assertEquals("zhangsan", info.getUsername());
        assertEquals("张三", info.getNickname());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), jsonCaptor.capture(), eq(Duration.ofSeconds(TIMEOUT_SECONDS)));

        // 1) key 规则必须与网关完全一致
        assertEquals(AuthSessionKeys.sessionKey(info.getToken()), keyCaptor.getValue());
        assertTrue(keyCaptor.getValue().startsWith("auth:session:"));

        // 2) value 必须能被独立反序列化成 AuthSession（模拟网关侧的读取）
        AuthSession session = objectMapper.readValue(jsonCaptor.getValue(), AuthSession.class);
        assertEquals(info.getToken(), session.getToken());
        assertEquals(2L, session.getUserId());
        assertEquals("zhangsan", session.getUsername());
        assertEquals("张三", session.getNickname());
        assertNotNull(session.getLoginTime(), "登录时间不能为空，token 的签发时刻是审计与超时排查的依据");

        // 3) loginTime 用 epoch 毫秒（数字）而非日期字符串，避免双方 Jackson 时间模块配置不一致
        assertTrue(jsonCaptor.getValue().contains("\"loginTime\":" + session.getLoginTime()),
                "loginTime 必须序列化为 epoch 毫秒数字：" + jsonCaptor.getValue());
    }

    @Test
    void issueGeneratesUniqueTokenPerCall() {
        String first = service.issue(1L, "admin", "管理员").getToken();
        String second = service.issue(1L, "admin", "管理员").getToken();

        assertNotEquals(first, second);
        // UUID 去掉中划线后为 32 位十六进制
        assertEquals(32, first.length());
    }

    @Test
    void revokeDeletesSessionKey() {
        service.revoke("abc123");

        verify(redisTemplate).delete("auth:session:abc123");
    }

    @Test
    void revokeIsNoOpForBlankToken() {
        service.revoke(null);
        service.revoke("");

        // 不能因为空令牌就发出 DEL ''（虽然无害，但属于无谓的 Redis 调用）
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void revokeIsIdempotent() {
        // DEL 不存在的 key 返回 false，不应抛异常：重复注销必须是安全的
        when(redisTemplate.delete(anyString())).thenReturn(false);

        assertDoesNotThrow(() -> service.revoke("not-exists"));
        verify(redisTemplate).delete("auth:session:not-exists");
    }

    @Test
    void issueFailsFastWhenSessionNotSerializable() {
        // 用一个会抛异常的 ObjectMapper 模拟「AuthSession 被改成不可序列化类型」的编码缺陷：
        // 这种错误必须在登录时就炸出来，而不是等到网关读会话时才莫名 401。
        // 注意 Jackson 3 的 JacksonException 构造器是 protected 的，所以用匿名子类来构造实例。
        ObjectMapper broken = mock(ObjectMapper.class);
        when(broken.writeValueAsString(any())).thenThrow(new JacksonException("boom") {
        });
        SessionTokenService brokenService = new SessionTokenService(redisTemplate, broken, TIMEOUT_SECONDS);

        assertThrows(IllegalStateException.class, () -> brokenService.issue(1L, "admin", "管理员"));
        // 没序列化成功就绝不能往 Redis 写脏数据
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }
}
