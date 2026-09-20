package com.jwy.scd.gateway;

import com.jwy.scd.api.auth.AuthHeaders;
import com.jwy.scd.api.auth.session.AuthSession;
import com.jwy.scd.gateway.config.AuthProperties;
import com.jwy.scd.gateway.filter.TokenAuthFilter;
import com.jwy.scd.gateway.session.RedisSessionReader;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 网关鉴权过滤器（{@link TokenAuthFilter}）单元测试 —— Redis Session Token 方案。
 *
 * <p>纯 Mockito 单测：mock 掉 {@link RedisSessionReader}（真实 Redis 读写属于端到端场景）。
 * 覆盖：白名单放行 / OPTIONS 放行 / 默认拦截 / 无令牌 401 / 会话不存在 401 /
 * Redis 不可用 503 / 会话有效放行 / <b>身份头注入与防伪造</b>。
 */
class TokenAuthFilterTest {

    private static final List<String> WHITELIST = List.of("/api/auth/login", "/api/public/**");

    private final RedisSessionReader sessionReader = mock(RedisSessionReader.class);

    private AuthProperties authProperties;

    private TokenAuthFilter filter;

    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        authProperties.setWhitelist(WHITELIST);
        filter = new TokenAuthFilter(sessionReader, new ObjectMapper(), authProperties);
        response = new MockHttpServletResponse();
    }

    private static MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    private static AuthSession session(Long userId, String username, String nickname) {
        AuthSession s = new AuthSession();
        s.setToken("t");
        s.setUserId(userId);
        s.setUsername(username);
        s.setNickname(nickname);
        s.setLoginTime(1789000000000L);
        return s;
    }

    /** 走一遍过滤器，返回最终交给责任链的请求（null 表示被拦下） */
    private HttpServletRequest run(HttpServletRequest request, MockFilterChain chain) throws Exception {
        filter.doFilter(request, response, chain);
        return (HttpServletRequest) chain.getRequest();
    }

    @Test
    void whitelistPathPassesWithoutToken() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        assertNotNull(run(request("POST", "/api/auth/login"), chain), "登录接口必须免鉴权放行");
        assertEquals(200, response.getStatus());
        verifyNoInteractions(sessionReader);
    }

    @Test
    void whitelistSupportsAntPattern() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        assertNotNull(run(request("GET", "/api/public/notice/1"), chain), "Ant 通配白名单应生效");
        verifyNoInteractions(sessionReader);
    }

    @Test
    void optionsPreflightPasses() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        assertNotNull(run(request("OPTIONS", "/api/sys-user/list"), chain), "CORS 预检应放行");
    }

    @Test
    void unknownPathIsRejectedByDefault() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        // 关键安全属性：判据是「在不在白名单」，而不是「有没有 /api/ 前缀」。
        // 将来新增一条不带 /api/ 前缀的路由，也不会被静默绕过鉴权。
        assertNull(run(request("GET", "/internal/secret"), chain), "非白名单路径应默认拦截");
        assertEquals(401, response.getStatus());
    }

    @Test
    void logoutIsNotWhitelisted() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        assertNull(run(request("POST", "/api/auth/logout"), chain), "注销需要先持有有效令牌");
        assertEquals(401, response.getStatus());
    }

    @Test
    void missingTokenRejectedWith401() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = request("GET", "/api/sys-user/list");

        assertNull(run(request, chain));
        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"), "RFC 7235 要求 401 带 WWW-Authenticate");
        assertTrue(response.getContentType().startsWith("application/problem+json"));
        assertTrue(response.getContentAsString().contains("未提供访问令牌"));
        verifyNoInteractions(sessionReader);
    }

    @Test
    void malformedAuthorizationHeaderRejected() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = request("GET", "/api/sys-user/list");
        // 少了 Bearer 前缀
        request.addHeader("Authorization", "abc123");

        assertNull(run(request, chain));
        assertEquals(401, response.getStatus());
        verifyNoInteractions(sessionReader);
    }

    @Test
    void expiredOrUnknownTokenRejectedWith401() throws Exception {
        when(sessionReader.find(anyString())).thenReturn(null);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = request("GET", "/api/order/list");
        request.addHeader("Authorization", "Bearer expired-token");

        assertNull(run(request, chain));
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("令牌无效或已过期"));
    }

    @Test
    void redisUnavailableRejectedWith503NotUnauthorized() throws Exception {
        when(sessionReader.find(anyString())).thenThrow(new RuntimeException("connection refused"));
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = request("GET", "/api/sys-user/list");
        request.addHeader("Authorization", "Bearer abc123");

        assertNull(run(request, chain));
        // 必须是 503 而不是 401：否则 Redis 一抖动，所有在线用户都会被前端判定为「登录失效」并清掉登录态
        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("认证会话存储不可用"));
    }

    @Test
    void validSessionPassesAndInjectsIdentityHeaders() throws Exception {
        when(sessionReader.find("abc123")).thenReturn(session(2L, "zhangsan", "张三"));
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = request("GET", "/api/sys-user/list");
        request.addHeader("Authorization", "Bearer abc123");

        HttpServletRequest forwarded = run(request, chain);

        assertNotNull(forwarded, "有效会话应放行");
        assertEquals("2", forwarded.getHeader(AuthHeaders.USER_ID));
        assertEquals("zhangsan", forwarded.getHeader(AuthHeaders.USERNAME));
        // 昵称刻意不透传：HTTP 头只能放 ASCII，中文会被静默替换成 "?"（端到端实测发现）
        assertNull(forwarded.getHeader("X-User-Nickname"));
    }

    @Test
    void clientSuppliedIdentityHeadersAreOverridden() throws Exception {
        when(sessionReader.find("abc123")).thenReturn(session(999L, "zhangsan", "张三"));
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = request("POST", "/api/order");
        request.addHeader("Authorization", "Bearer abc123");
        // 攻击者试图冒充管理员（ID=1）
        request.addHeader(AuthHeaders.USER_ID, "1");
        request.addHeader("x-username", "admin");

        HttpServletRequest forwarded = run(request, chain);

        assertNotNull(forwarded);
        // 必须只剩网关解析出的值：追加而不覆盖的话，下游 getHeader 拿到的是第一个（伪造的）值
        assertEquals("999", forwarded.getHeader(AuthHeaders.USER_ID));
        assertEquals("zhangsan", forwarded.getHeader(AuthHeaders.USERNAME));

        List<String> userIdValues = Collections.list(forwarded.getHeaders(AuthHeaders.USER_ID));
        assertEquals(List.of("999"), userIdValues, "客户端自带的同名头必须被完全丢弃");

        List<String> headerNames = Collections.list(forwarded.getHeaderNames());
        long occurrences = headerNames.stream().filter(n -> n.equalsIgnoreCase(AuthHeaders.USER_ID)).count();
        assertEquals(1, occurrences, "头名不应重复出现");
        assertFalse(headerNames.stream().anyMatch(n -> n.equalsIgnoreCase("x-username") && !n.equals(AuthHeaders.USERNAME)),
                "客户端的小写 x-username 不应原样透出");
    }

    @Test
    void sessionWithNullFieldsInjectsNothing() throws Exception {
        when(sessionReader.find(anyString())).thenReturn(session(null, null, null));
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = request("GET", "/api/product/list");
        request.addHeader("Authorization", "Bearer abc123");

        HttpServletRequest forwarded = run(request, chain);

        assertNotNull(forwarded, "会话存在即视为已认证，字段缺失不应拒绝请求");
        assertNull(forwarded.getHeader(AuthHeaders.USER_ID));
        assertNull(forwarded.getHeader(AuthHeaders.USERNAME));
    }

    @Test
    void emptyWhitelistDeniesEverything() throws Exception {
        // 配置丢失（app.auth.whitelist 缺失）时白名单为空 → 连登录都被拦。
        // 这是刻意的「安全的失败方向」：能立刻发现配置问题，而不是静默放行。
        authProperties.setWhitelist(List.of());
        MockFilterChain chain = new MockFilterChain();

        assertNull(run(request("POST", "/api/auth/login"), chain));
        assertEquals(401, response.getStatus());
    }
}
