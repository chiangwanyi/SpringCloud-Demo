package com.jwy.scd.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关鉴权配置（对应 application.yml 的 {@code app.auth.*}）。
 *
 * <p><b>⚠️ 为什么用 {@code @ConfigurationProperties} 而不是 {@code @Value("${app.auth.whitelist}")}？</b>
 * 这是一个很容易踩的坑：{@code @Value} 只能读取「单个字符串」形式的属性，
 * 而 YAML 里的<b>列表</b>会被 Spring Boot 展开成 {@code app.auth.whitelist[0]}、
 * {@code app.auth.whitelist[1]} 这样的索引键 —— 并不存在名为 {@code app.auth.whitelist} 的属性。
 * 于是 {@code @Value("${app.auth.whitelist:}")} 会静默地取到默认值空串，
 * 白名单变成空列表，表现为「<b>连登录接口都被 401 拦住</b>」，且不会有任何启动报错。
 *
 * <p>{@code @ConfigurationProperties} 走的是宽松绑定（relaxed binding），能正确绑定 YAML 列表、
 * 支持 {@code whitelist:} / {@code WHITELIST} / {@code whitelist[0]} 等多种写法，
 * 并且有类型校验、支持 IDE 提示，是配置绑定的推荐做法。
 */
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /**
     * 免鉴权路径白名单，支持 Ant 风格通配（如 {@code /api/public/**}）。
     *
     * <p>默认空集合 = 默认全部拦截。刻意不给宽松默认值：
     * 配置丢失时宁可把登录口也拦住（能立刻发现），也不要静默放开鉴权。
     */
    private List<String> whitelist = new ArrayList<>();

    /**
     * 会话续期时长（秒），必须与 service-auth 的 {@code app.auth.session-timeout} 保持一致，
     * 否则网关续期可能把 TTL 拉得比认证服务签发的更长。
     */
    private long sessionTimeout = 1800L;

    /**
     * 滑动过期开关：每次读到有效会话就续期（{@code EXPIRE}），用户持续活动就不会掉线。
     *
     * <p>代价是每个请求多一条 Redis 写命令；压测证明有影响时可关掉，
     * 或改为「仅在剩余 TTL 低于阈值时续期」、或换成一条 Lua 脚本把 GET+EXPIRE 合成单次往返。
     */
    private boolean refreshOnAccess = true;

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }

    public long getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public boolean isRefreshOnAccess() {
        return refreshOnAccess;
    }

    public void setRefreshOnAccess(boolean refreshOnAccess) {
        this.refreshOnAccess = refreshOnAccess;
    }
}
