package com.jwy.scd.gateway;

import com.jwy.scd.gateway.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuthProperties} 配置绑定测试。
 *
 * <p><b>这个测试是为一个真实踩过的坑写的回归测试</b>：
 * 原先白名单用 {@code @Value("${app.auth.whitelist:}")} 注入 {@code List<String>}，
 * 但 YAML 的列表在 Spring 内部会被展开成 {@code app.auth.whitelist[0]}、{@code [1]} 这样的索引键，
 * 并不存在名为 {@code app.auth.whitelist} 的属性 —— 于是 {@code @Value} 静默取到默认空串，
 * 白名单变成空列表，表现为「连登录接口都返回 401」，且启动阶段没有任何报错。
 * 改用 {@code @ConfigurationProperties} 后由宽松绑定负责，问题消失。
 */
class AuthPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthProperties.class)
    static class TestConfig {
    }

    /** YAML 列表被展开后的形态就是这种索引键，用来复现绑定场景 */
    @Test
    void bindsYamlStyleList() {
        runner.withPropertyValues(
                "app.auth.whitelist[0]=/api/auth/login",
                "app.auth.whitelist[1]=/api/public/**",
                "app.auth.session-timeout=600",
                "app.auth.refresh-on-access=false"
        ).run(context -> {
            AuthProperties props = context.getBean(AuthProperties.class);
            assertThat(props.getWhitelist()).containsExactly("/api/auth/login", "/api/public/**");
            assertThat(props.getSessionTimeout()).isEqualTo(600L);
            assertThat(props.isRefreshOnAccess()).isFalse();
        });
    }

    /** 直接加载真实的 application.yml，配置键被改名 / 删除时这个测试会失败 */
    @Test
    void realApplicationYmlBindsExpectedValues() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        assertThat(sources).isNotEmpty();

        runner.withInitializer(context -> sources
                .forEach(source -> context.getEnvironment().getPropertySources().addFirst(source))
        ).run(context -> {
            AuthProperties props = context.getBean(AuthProperties.class);

            // 登录必须是唯一的免鉴权入口，否则没人能拿到第一张票
            assertThat(props.getWhitelist()).contains("/api/auth/login");
            // 注销刻意不在白名单：必须先持有有效令牌才能注销自己的会话
            assertThat(props.getWhitelist()).doesNotContain("/api/auth/logout");

            assertThat(props.getSessionTimeout()).isEqualTo(1800L);
            assertThat(props.isRefreshOnAccess()).isTrue();
        });
    }

    /** 配置缺失时的兜底值必须是「安全的失败方向」：白名单为空 = 全部拦截 */
    @Test
    void defaultsAreSafe() {
        runner.run(context -> {
            AuthProperties props = context.getBean(AuthProperties.class);
            assertThat(props.getWhitelist()).isEmpty();
            assertThat(props.getSessionTimeout()).isEqualTo(1800L);
            assertThat(props.isRefreshOnAccess()).isTrue();
        });
    }
}
