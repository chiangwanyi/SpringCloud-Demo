package com.jwy.scd.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * service-order 的跨域（CORS）配置 —— 仅用于「直连本服务」的调试场景。
 *
 * <p><b>架构上的正解是配置在网关</b>（见 service-gateway 的 {@code CorsConfig}）：
 * 外部流量都经过网关，CORS 只写一处即可。本类存在的理由是<b>开发调试</b> ——
 * 把本服务单独起在 8083，用浏览器页面（如 tools/qps-tester.html）直接压测，
 * 不经过网关、也就不经过网关的 CORS 过滤器，所以需要本服务自己放行。
 *
 * <p><b>⚠️ 因此本类默认【关闭】，由开关 {@code app.cors.direct-enabled} 控制。</b>
 * 原因是实测过的硬约束：CORS 响应头<b>只能在输出链路的某一层写</b>。
 * 若本服务与网关同时开启，请求经网关转发时两边各写一遍，客户端会收到两个
 * {@code Access-Control-Allow-Origin}，浏览器直接报
 * {@code The 'Access-Control-Allow-Origin' header contains multiple values} 并拒绝响应
 * （实测数据：直连 8083 时 1 个，经网关时 2 个）。
 *
 * <p>所以默认遵循微服务惯例 —— <b>CORS 只在网关配置一处</b>，本类保持关闭。
 * 仅当需要「不经网关、用浏览器页面直连本服务调试」时，才手动打开：
 * <pre>
 * java -jar service-order.jar --server.port=8083 --app.cors.direct-enabled=true
 * </pre>
 *
 * <p><b>⚠️ 实现细节</b>：这里同样用 Servlet {@link CorsFilter} 而不是
 * {@code WebMvcConfigurer#addCorsMappings}。后者只对 Spring MVC 的
 * {@code @RequestMapping} 处理器生效，覆盖面窄；{@link CorsFilter} 是过滤器，
 * 对所有路径（含全局异常处理器产出的响应）一律生效，语义更简单可靠。
 */
@Configuration
// 默认不生效：只有显式传入 app.cors.direct-enabled=true 才注册本过滤器。
// matchIfMissing 保持默认 false，即「配置缺失 = 关闭」，这是安全的失败方向。
@ConditionalOnProperty(name = "app.cors.direct-enabled", havingValue = "true")
public class CorsConfig {

    /**
     * 注册 CORS 过滤器，顺序置于最高优先级 —— 保证在任何业务过滤器 /
     * 拦截器之前执行，这样即便是被后续逻辑拒绝的请求，响应上也带着
     * CORS 头，浏览器能读到真实的错误信息而非笼统的「CORS 错误」。
     *
     * @return CORS 过滤器的注册信息
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfiguration config = new CorsConfiguration();

        // 开发期放开全部来源。注意 allowedOrigins("*") 与 allowCredentials(true) 互斥，
        // 必须用 allowedOriginPatterns；它会回显请求的真实 Origin（含 file:// 的 "null"）。
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        // 预检结果缓存 1 小时：压测时浏览器不会为每个请求都先发一次 OPTIONS。
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("corsFilter");
        return registration;
    }
}
