# 项目长期记忆（springcloud-demo）

## 技术栈与环境兼容性（重要）
- 父工程 `springcloud-demo`：packaging=pom，groupId=com.jwy，Spring Boot **4.0.8**（即 Spring Framework **7.0.9**）作为 parent。
- **MyBatis-Plus 必须用 `mybatis-plus-spring-boot4-starter`（本地可用 3.5.16）**，不要用 `mybatis-plus-boot-starter`（老版会拉 mybatis-spring 3.x，与 Spring 7 的 `factoryBeanObjectType` 冲突，报 Invalid bean definition）。
- **数据库已从 H2 切到 MySQL**：`service-system` / `service-auth` 的 pom 用 `com.mysql:mysql-connector-j`（runtime scope），application.yml 用 `com.mysql.cj.jdbc.Driver` + `jdbc:mysql://rxs:3306/<db>`。连接信息（主机 rxs / 端口 3306 / root / 123456）见 `mysql.md`。现有 `schema.sql`（AUTO_INCREMENT / TINYINT / DATETIME）已兼容 MySQL，`sql.init.mode=always` 启动时仍会 DROP+CREATE 表（会从真实库清空数据）。
- 本地 Maven 仓库初始离线，但 `settings.xml` 配置了可用代理（`127.0.0.1:7897`，环境另有 `127.0.0.1:60711`），可访问 Maven Central 下载依赖。父 POM `dependencyManagement` import 两个 BOM：`spring-cloud-alibaba-dependencies:2025.1.0.0` 与 `spring-cloud-dependencies:2025.1.0`（见 Nacos 段）。openfeign 在 api 模块仍硬编码 `4.1.0`（`<optional>true</optional>`）。

## ⚠️ Spring Cloud 版本线铁律（Boot 4.0.8 必读，曾因此三服务启动失败）
- **Spring Boot 4.0.8（GA）只能搭配 Spring Cloud `2025.1.x` 发布列车（对应 `spring-cloud-*` **5.0.x** 线）**。
- **`2025.0.x` 列车（对应 `spring-cloud-*` **4.3.x** 线）编译于 Boot 4.0 里程碑版本，与 Boot 4.0.8 GA 不兼容，绝不能再用。**
- 错误现象（已踩坑）：用 `2025.0.x` BOM 时——
  - 网关报 `NoClassDefFoundError: org/springframework/boot/http/client/HttpRedirects`（被 `spring-cloud-gateway-server-mvc:4.3.5` 拉入，引用了 Boot 4.0.8 已移走的 `HttpRedirects`）；
  - service 模块报 `NoClassDefFoundError: org/springframework/boot/web/context/WebServerInitializedEvent`（被 `spring-cloud-commons:4.3.3`/`spring-cloud-context:4.3.3` 拉入，Boot 4.0.8 已重定位该类）。
- 同理 Spring Cloud Alibaba：`2025.0.0.0`（parent `spring-cloud-dependencies-parent:4.3.0`，4.3.x 线）✗；必须用 `2025.1.0.0`（parent `5.0.0`，5.0.x 线）✓。
- **正确组合（当前生效）**：`spring-cloud-alibaba-dependencies:2025.1.0.0` + `spring-cloud-dependencies:2025.1.0`。其余 spring-cloud-*（commons/context/loadbalancer/gateway-server-webmvc）均随之落到 5.0.x。
- 网关 `spring-cloud-gateway-server-webmvc` 用 5.0.x 后**不再依赖** `spring-cloud-gateway-server-mvc`（后者根本没有 5.0.x，最新仅 4.3.5，是 2025.0.x 线专属），从根上避免 `HttpRedirects` 错误。

## 模块约定（微服务）
- `service-system`：实现模块（entity / mapper / service / controller / 启动类 / 测试）。
- `service-system-api`：对外契约模块（DTO + Feign 契约接口），供其他微服务依赖。
  - api 模块内的 `spring-cloud-starter-openfeign` 设为 `<optional>true</optional>`，避免实现模块被传递引入 spring-cloud 自动配置（Boot4 下 SimpleDiscoveryClientAutoConfiguration 崩溃）。
  - 消费方需自行加 openfeign 依赖 + `@EnableFeignClients`，并用 `interface X extends SysUserApi` 声明 Feign 客户端。
- 契约接口同时用 `@RequestMapping`（Spring MVC，Controller 继承）与 `@FeignClient`（Feign，消费方启用），保证 HTTP 路径一致。
- `service-gateway`：API 网关（Spring Cloud Gateway **webmvc 风味 5.0.3**）。Boot 4 必须用 `spring-cloud-starter-gateway-server-webmvc`（非旧 `spring-cloud-starter-gateway`），配置根 `spring.cloud.gateway.server.webmvc.routes`。路由 uri 已改为 `lb://service-system`、`lb://service-auth`（走 Nacos 服务发现 + LoadBalancer），并额外引入 `spring-cloud-starter-loadbalancer`（lb:// 必需）。
- **API 文档用 SpringDoc OpenAPI（非 Springfox）**：Boot 4 必须用 **3.x 线**（`springdoc-openapi-starter-webmvc-ui:3.1.1`，本地离线仓库的 `2.8.16` 是 Boot 3 线不可用于 Boot 4）。api 契约模块只引 `webmvc-api`（注解类），实现模块引 `webmvc-ui`（UI+端点）。文档注解标在 api 契约接口/DTO 上，实现 `implements` 自动继承。暴露 `/v3/api-docs`（JSON）与 `/swagger-ui.html`。

## Nacos 服务注册与发现（2026-09-20 接入）
- Nacos 服务器（局域网）：控制台 `http://rxs:8080/next/#/login`，客户端 API 端口 `8848`（gRPC `9848`）。账号 `nacos` / `123456`（见 `nacos.md`）。docker 已开启认证（`NACOS_AUTH_TOKEN` 等），故客户端必须带 `username/password=nacos/123456`。
- 父 POM `dependencyManagement` 同时 import 两个 BOM：`com.alibaba.cloud:spring-cloud-alibaba-dependencies:2025.1.0.0`（先 import）与 `org.springframework.cloud:spring-cloud-dependencies:2025.1.0`（后 import 优先生效，统一 spring-cloud-* 到 5.0.x 线，与网关一致）。**注意：这两个版本曾误配成 `2025.0.0.0`/`2025.0.3`（4.3.x 线），导致三服务启动失败，已修正为 5.0.x 线（详见上文「版本线铁律」）。**
- 三个服务（service-system / service-auth / service-gateway）均引入 `spring-cloud-starter-alibaba-nacos-discovery`，并在 `application.yml` 配置 `spring.cloud.nacos.discovery.server-addr: rxs:8848` + 认证账号；启动类加 `@EnableDiscoveryClient`。
- 网关路由 uri 改 `lb://service-system`、`lb://service-auth`（需 `spring-cloud-starter-loadbalancer`）。服务名取各模块 `spring.application.name`。
- 注意：仅接入“服务注册/发现”，未接入 Nacos Config 配置中心（如需把 application.yml 外置到 Nacos，需再加 `spring-cloud-starter-alibaba-nacos-config` + `spring.config.import`）。
- 验证：离线 `mvn -B -DskipTests compile` 全模块 BUILD SUCCESS（nacos-discovery / loadbalancer 依赖经代理从 Maven Central 下载成功）。

## 工程操作注意
- 手动重命名模块目录后，务必同时清理孤儿 `.iml` 与 `.idea`，否则 IDEA 会反复重建旧模块目录。
- 测试/演示资源（schema.sql、application.yml）有时会被外部进程清除，重建时放到 `src/main/resources` 让运行与测试共用。
