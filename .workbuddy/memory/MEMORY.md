# 项目长期记忆（springcloud-demo）

## 技术栈与环境兼容性（重要）
- 父工程 `springcloud-demo`：packaging=pom，groupId=com.jwy，Spring Boot **4.0.8**（即 Spring Framework **7.0.9**）作为 parent。
- **MyBatis-Plus 必须用 `mybatis-plus-spring-boot4-starter`（本地可用 3.5.16）**，不要用 `mybatis-plus-boot-starter`（老版会拉 mybatis-spring 3.x，与 Spring 7 的 `factoryBeanObjectType` 冲突，报 Invalid bean definition）。
- 使用 H2 时必须显式声明 `com.h2database:h2`（runtime scope），否则 `Cannot load driver class: org.h2.Driver`。
- 本地 Maven 仓库**没有** spring-cloud / openfeign 的 BOM；openfeign 仅 `4.1.0` 可用，且无与 Boot4 匹配的 spring-cloud-dependencies。

## 模块约定（微服务）
- `service-system`：实现模块（entity / mapper / service / controller / 启动类 / 测试）。
- `service-system-api`：对外契约模块（DTO + Feign 契约接口），供其他微服务依赖。
  - api 模块内的 `spring-cloud-starter-openfeign` 设为 `<optional>true</optional>`，避免实现模块被传递引入 spring-cloud 自动配置（Boot4 下 SimpleDiscoveryClientAutoConfiguration 崩溃）。
  - 消费方需自行加 openfeign 依赖 + `@EnableFeignClients`，并用 `interface X extends SysUserApi` 声明 Feign 客户端。
- 契约接口同时用 `@RequestMapping`（Spring MVC，Controller 继承）与 `@FeignClient`（Feign，消费方启用），保证 HTTP 路径一致。
- `service-gateway`：API 网关（Spring Cloud Gateway **webmvc 风味 5.0.3**）。Boot 4 必须用 `spring-cloud-starter-gateway-server-webmvc`（非旧 `spring-cloud-starter-gateway`），配置根 `spring.cloud.gateway.server.webmvc.routes`。uri 当前写死 localhost，待接 Nacos 改 `lb://service-name`。
- **API 文档用 SpringDoc OpenAPI（非 Springfox）**：Boot 4 必须用 **3.x 线**（`springdoc-openapi-starter-webmvc-ui:3.1.1`，本地离线仓库的 `2.8.16` 是 Boot 3 线不可用于 Boot 4）。api 契约模块只引 `webmvc-api`（注解类），实现模块引 `webmvc-ui`（UI+端点）。文档注解标在 api 契约接口/DTO 上，实现 `implements` 自动继承。暴露 `/v3/api-docs`（JSON）与 `/swagger-ui.html`。

## 工程操作注意
- 手动重命名模块目录后，务必同时清理孤儿 `.iml` 与 `.idea`，否则 IDEA 会反复重建旧模块目录。
- 测试/演示资源（schema.sql、application.yml）有时会被外部进程清除，重建时放到 `src/main/resources` 让运行与测试共用。
