package com.jwy.scd.gateway.filter;

import com.jwy.scd.api.auth.AuthHeaders;
import com.jwy.scd.api.auth.session.AuthSession;
import com.jwy.scd.gateway.config.AuthProperties;
import com.jwy.scd.gateway.session.RedisSessionReader;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * 网关统一鉴权过滤器（WebMVC / Servlet 风格）—— <b>Redis Session Token 方案</b>。
 *
 * <p><b>职责</b>：作为「唯一入口」的守门人，对每个进入网关的请求先查会话再放行。
 * 令牌无效的请求在网关就被 401 拦下，根本到不了后端业务服务。
 *
 * <p><b>工作流程</b>（全程 <u>零服务间调用</u>）：
 * <ol>
 *     <li>白名单路径（如登录接口）直接放行；OPTIONS 预检放行；</li>
 *     <li>从 {@code Authorization: Bearer <token>} 头解析令牌；</li>
 *     <li>拿令牌去 Redis 查会话（{@link RedisSessionReader}）——
 *         查得到即有效，顺带拿到 {@code userId / username / nickname}；</li>
 *     <li>无票 / 会话不存在 → 401；Redis 不可用 → 503；</li>
 *     <li>验票通过 → 把身份信息注入请求头（并覆盖客户端伪造的同名头）后放行转发。</li>
 * </ol>
 *
 * <p><b>与「远程调用认证服务校验」方案的区别</b>：
 * 本过滤器没有 Feign、没有认证服务的任何客户端，只有一次 Redis GET。
 * 好处是：0 次额外 RPC、网关不依赖认证服务可用性（auth 重启不影响已登录用户）、
 * 且能拿到完整会话信息用于身份透传。详见 {@code AuthApi} 的类注释。
 *
 * <p><b>设计要点</b>：
 * <ul>
 *     <li><b>默认拦截 + 白名单放行</b>（而不是「只拦 /api/ 前缀」）：
 *         白名单里没有的路径一律要求令牌。这样将来新增一条不带 {@code /api/} 前缀的路由时，
 *         鉴权不会被静默绕过 —— 「默认安全」比「默认放行」可靠得多；</li>
 *     <li>选 {@link OncePerRequestFilter}（Servlet Filter）而非 WebFlux 的 {@code GlobalFilter}：
 *         本项目网关是 spring-cloud-gateway-server-webmvc（WebMVC 风味），与三个后端服务保持一致；</li>
 *     <li>{@code @Order(HIGHEST_PRECEDENCE + 10)}：保证在任何业务过滤器之前执行；</li>
 *     <li>失败响应统一使用 RFC 7807 ProblemDetail，与三个后端服务保持一致，
 *         并按 RFC 7235 补上 {@code WWW-Authenticate: Bearer} 头。</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TokenAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TokenAuthFilter.class);

    private final RedisSessionReader sessionReader;

    private final ObjectMapper objectMapper;

    /** 鉴权配置（白名单 / 会话续期参数） */
    private final AuthProperties authProperties;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public TokenAuthFilter(RedisSessionReader sessionReader, ObjectMapper objectMapper, AuthProperties authProperties) {
        this.sessionReader = sessionReader;
        this.objectMapper = objectMapper;
        this.authProperties = authProperties;
    }

    /**
     * 判定哪些请求完全不进入鉴权逻辑。
     *
     * <p>注意 {@link OncePerRequestFilter} 默认会跳过 Spring MVC 的 ERROR 派发
     * （{@code shouldNotFilterErrorDispatch()} 默认返回 true），所以转到 {@code /error} 的内部派发
     * 不会再被这里拦一次，不需要把 {@code /error} 写进白名单。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // CORS 预检请求不携带自定义头，拦下来会导致跨域直接失败
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        List<String> whitelist = authProperties.getWhitelist();
        if (whitelist == null || whitelist.isEmpty()) {
            // 配置缺失时白名单为空 → 连登录都被拦。这是刻意的「安全的失败方向」，
            // 能立刻发现配置问题，而不是静默放行。
            return false;
        }
        String path = request.getRequestURI();
        for (String pattern : whitelist) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        // 没命中白名单 → 需要鉴权
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // ---------- 1. 取令牌 ----------
        String token = AuthHeaders.resolveToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (!StringUtils.hasText(token)) {
            writeProblem(response, request, HttpStatus.UNAUTHORIZED, "未提供访问令牌，请先登录");
            return;
        }

        // ---------- 2. 查 Redis 会话（本方案唯一的「鉴权动作」） ----------
        AuthSession session;
        try {
            session = sessionReader.find(token);
        } catch (Exception ex) {
            // Redis 不可用：必须与「会话不存在」区分开。
            // 若也返回 401，Redis 抖动时会把所有在线用户误判为「登录已失效」并清掉前端本地登录态，
            // 用户体验与排障成本都很差。返回 503 才是准确的语义。
            log.error("读取会话失败（Redis 不可用？）：{}", ex.getMessage(), ex);
            writeProblem(response, request, HttpStatus.SERVICE_UNAVAILABLE, "认证会话存储不可用，请稍后重试");
            return;
        }
        if (session == null) {
            writeProblem(response, request, HttpStatus.UNAUTHORIZED, "令牌无效或已过期，请重新登录");
            return;
        }

        // ---------- 3. 验票通过：注入身份头后放行 ----------
        if (log.isDebugEnabled()) {
            log.debug("会话有效：username={}, userId={}, {} {}", session.getUsername(), session.getUserId(),
                    request.getMethod(), request.getRequestURI());
        }
        filterChain.doFilter(new SessionHeaderRequestWrapper(request, session), response);
    }

    /** 统一输出 RFC 7807 ProblemDetail（与三个后端服务的错误契约一致） */
    private void writeProblem(HttpServletResponse response, HttpServletRequest request, HttpStatus status, String detail)
            throws IOException {
        // RFC 7235：401 响应必须带 WWW-Authenticate，客户端据此知道该用 Bearer 方案重新认证
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(problemDetail));
    }
}
