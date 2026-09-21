package com.jwy.scd.gateway.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * 网关全局跨域（CORS）配置。
 *
 * <p><b>为什么 CORS 要放在网关？</b>
 * 网关是外部流量的唯一入口，而 CORS 是典型的「横切关注点」——
 * 放在这里只需配置一处，所有经过网关的路由（sys-user / auth / order / product）自动全部受益；
 * 若分散到各个后端服务里，既重复又容易漏配，还会出现下面说的「重复响应头」问题。
 *
 * <p><b>⚠️ 为什么不用 {@code spring.cloud.gateway.server.webmvc.globalcors}？</b>
 * 那是 <u>WebFlux（响应式）风味</u>网关的配置项。本项目用的是 Boot 4 的
 * <u>WebMVC（Servlet/Tomcat）风味</u>网关 —— 实测该 flavor 的
 * {@code spring-configuration-metadata.json} 里<b>根本不存在任何 cors 属性</b>，
 * 网上大量教程照搬的 {@code globalcors.cors-configurations} 写上去会<b>静默失效</b>
 * （不报错、也不生效，最难排查）。因为网关本身就是一个标准 Servlet 应用，
 * 所以这里用 Spring MVC 体系里最正统的 {@link CorsFilter}。
 *
 * <p><b>为什么是 {@link FilterRegistrationBean} 而不是直接 {@code @Bean CorsFilter}？</b>
 * 需要显式指定执行顺序。CORS 过滤器必须排在 {@code TokenAuthFilter}
 * （{@code @Order(HIGHEST_PRECEDENCE + 10)}）之前，原因有两点：
 * <ol>
 *     <li>响应头在进入过滤器链时就被写入，即使后续被鉴权过滤器拒绝（401），
 *         浏览器也能正常读到这个 401 响应体，而不是只看到一个语焉不详的「CORS 错误」；</li>
 *     <li>OPTIONS 预检请求直接由此过滤器应答，不进入后续链路
 *         （{@link CorsFilter} 内部对预检请求会提前返回，不再调用 {@code doFilter}）。</li>
 * </ol>
 *
 * <p><b>安全提示（生产环境必看）</b>：这里用 {@code allowedOriginPatterns("*")}
 * 是为了让本机各种调试页面（如 {@code http://127.0.0.1:8090} 的压测工具、
 * 甚至 {@code file://} 打开的本地 HTML）都能直接调用。生产环境应改为明确列举的可信域名，
 * 例如 {@code List.of("https://admin.example.com")}，避免任意站点都能读取接口响应。
 */
@Configuration
public class CorsConfig {

    /**
     * 注册全局 CORS 过滤器。
     *
     * @return 注册信息，order 设为 {@link Ordered#HIGHEST_PRECEDENCE} 使其先于鉴权过滤器执行
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfiguration config = new CorsConfiguration();

        // 允许的来源。用 allowedOriginPatterns 而不是 allowedOrigins：
        //   - allowedOrigins("*") 与 allowCredentials(true) 互斥，Spring 会直接抛异常；
        //   - allowedOriginPatterns("*") 会把请求里的 Origin 原样回显，
        //     因此也能覆盖 file:// 页面发来的 "null" 来源。
        config.setAllowedOriginPatterns(List.of("*"));

        // 允许的请求方法。OPTIONS 必须在这里 —— 它就是预检请求本身用的方法。
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"));

        // 允许客户端携带的请求头（Authorization 就是靠它放行的，不能少）。
        config.setAllowedHeaders(List.of("*"));

        // 允许浏览器读取的响应头。默认只暴露 6 个「安全头」，
        // 这里放开全部，方便调试时读取 X-User-Id 等网关注入的身份头。
        config.setExposedHeaders(List.of("*"));

        // 允许携带凭证（Cookie / Authorization）。关闭后浏览器不会带上凭证类信息。
        config.setAllowCredentials(true);

        // 预检结果缓存 1 小时：浏览器在此期间不再重复发 OPTIONS，减少一半请求量。
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // /** = 对所有路径生效（含网关自身的路由与后续新增的路由）
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("corsFilter");
        return registration;
    }
}
