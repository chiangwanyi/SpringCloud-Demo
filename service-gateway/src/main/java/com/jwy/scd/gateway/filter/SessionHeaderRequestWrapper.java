package com.jwy.scd.gateway.filter;

import com.jwy.scd.api.auth.AuthHeaders;
import com.jwy.scd.api.auth.session.AuthSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 请求包装器：把「已认证用户身份」以请求头的形式注入，并<b>覆盖掉客户端自带的同名头</b>。
 *
 * <p><b>为什么必须用包装器，而不是直接 {@code request.getHeader()}？</b>
 * Servlet 的 {@code HttpServletRequest} 是不可变的，没有 {@code setHeader}。
 * 网关把请求转发给下游时，是从 {@code HttpServletRequest} 上枚举头部的，
 * 所以只要用一个 {@code HttpServletRequestWrapper} 子类覆写取值方法，
 * 转发时下游看到的就是被改写后的头部。
 *
 * <p><b>为什么必须「覆盖」而不是「追加」？—— 这是身份伪造防线（务必理解）</b>
 * 假设直接追加：外部用户构造 {@code X-User-Id: 1}（管理员 ID）发到网关，
 * 网关再追加自己的 {@code X-User-Id: 999}。下游如果用 {@code getHeader()} 取，
 * 拿到的是<b>第一个</b>值，也就是攻击者伪造的 1 —— 越权成功。
 * 因此本类对这三个头采取「<b>先删除客户端带来的所有同名值，再放入网关解析出的唯一值</b>」
 * 的策略：{@link #getHeaderNames()} 不再暴露客户端原来的头名，
 * {@link #getHeaders(String)} 只返回网关注入的那一个值。
 *
 * <p>同理，下游服务只有在「只能通过网关访问」的前提下才能信任这些头
 * （也就是说：不要把业务服务的端口直接暴露给公网）。
 */
class SessionHeaderRequestWrapper extends HttpServletRequestWrapper {

    /** 网关注入的头：名称 → 值。用 LinkedHashMap 保证注入顺序稳定，便于日志与排障。 */
    private final Map<String, String> injectedHeaders;

    SessionHeaderRequestWrapper(HttpServletRequest request, AuthSession session) {
        super(request);

        Map<String, String> headers = new LinkedHashMap<>();
        if (session.getUserId() != null) {
            headers.put(AuthHeaders.USER_ID, String.valueOf(session.getUserId()));
        }
        if (session.getUsername() != null) {
            headers.put(AuthHeaders.USERNAME, session.getUsername());
        }
        // 注意：这里刻意【不】注入用户昵称。HTTP 头只能承载 ASCII，
        // 中文昵称（如「张三」）会在发送端被静默替换成 "??"（端到端测试实测发现）。
        // 详见 AuthHeaders 的类注释。
        this.injectedHeaders = headers;
    }

    @Override
    public String getHeader(String name) {
        String injected = findInjected(name);
        return injected != null ? injected : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String injected = findInjected(name);
        // 只返回网关注入的值，客户端自带的同名头一律丢弃
        return injected != null
                ? Collections.enumeration(Collections.singletonList(injected))
                : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new LinkedHashSet<>();
        Enumeration<String> original = super.getHeaderNames();
        if (original != null) {
            while (original.hasMoreElements()) {
                String name = original.nextElement();
                // 被接管的头名不再透出（无论客户端用大写还是小写写的）
                if (findInjected(name) == null) {
                    names.add(name);
                }
            }
        }
        names.addAll(injectedHeaders.keySet());
        return Collections.enumeration(names);
    }

    /** 按 HTTP 规范做大小写不敏感匹配（X-User-Id / x-user-id / X-USER-ID 等价） */
    private String findInjected(String name) {
        if (name == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : injectedHeaders.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
