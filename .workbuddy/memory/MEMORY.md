# 项目长期记忆（springcloud-demo）

## ⚠️ 版本铁律（Spring Boot 4.0.8 / Spring Framework 7）
- 父 POM：packaging=pom，groupId=com.jwy，Spring Boot **4.0.8**。
- **Spring Cloud 必须 `2025.1.x` 列车（`spring-cloud-*` 为 5.0.x）**；`2025.0.x`（4.3.x 线）编译于 Boot 4 里程碑版，与 4.0.8 GA **不兼容，绝不能再用**。现象：网关 `NoClassDefFoundError: HttpRedirects`、service 端 `WebServerInitializedEvent`。
- 正确组合（dependencyManagement 依次 import）：`spring-cloud-alibaba-dependencies:2025.1.0.0` + `spring-cloud-dependencies:2025.1.0`。**所有 spring-cloud-* 依赖一律不写 `<version>`。**
- 网关用 `spring-cloud-starter-gateway-server-webmvc`（Boot 4 改名，旧 `spring-cloud-starter-gateway` 失效），配置根 `spring.cloud.gateway.server.webmvc.routes`，`lb://` 需额外引 `spring-cloud-starter-loadbalancer`。
- **JSON 库是 Jackson 3，包名 `tools.jackson.*`**。自动配置的 `ObjectMapper` bean 类型是 `tools.jackson.databind.ObjectMapper`；**注入老包名 `com.fasterxml.jackson.databind.ObjectMapper` 会启动失败**（Jackson 2 只是被 nacos/yaml 间接带入，不再自动配置）。`JacksonException extends RuntimeException`（非受检），构造器 `protected`（测试造实例要用匿名子类）。
- MyBatis-Plus 用 `mybatis-plus-spring-boot4-starter`；API 文档用 SpringDoc **3.x**（2.8.x 是 Boot 3 线）。
- **YAML 列表（list）只能用 `@ConfigurationProperties` 绑定，不能用 `@Value`**：YAML 列表在 Spring 内部是 `xxx[0]`/`xxx[1]` 索引键，不存在 `xxx` 属性，`@Value("${xxx:}")` 会静默取默认空值。踩过：网关白名单为空 → 连登录都 401，且启动无任何报错。
- **HTTP 头只能承载 ASCII**：非 ASCII（如中文昵称「张三」）会被发送端**静默替换成 `?`**。故网关只透传 `X-User-Id`/`X-Username`，不透传昵称。
- MySQL 见 `mysql.md`；`sql.init.mode=always` 启动会 DROP+CREATE **清空真实库**。Nacos 见 `nacos.md`（rxs:8848，nacos/123456）；service-system 走 Nacos Config 拉 datasource（`spring.config.import: nacos:<服务名>.yaml`，缺 dataId 启动失败），Nacos 3.x 配置 API 是 `/nacos/v3/admin/cs/config`（v1 已 404）。
- **Redis**：rxs:6379 / **database 2** / 无密码，服务端版本 5.0.14（别用 6.2+ 才有的命令）。会话 key `auth:session:<token>`。

## ⚠️ Lettuce 主机名解析坑
Lettuce 默认用 **Netty 自带 DNS 解析器**（只发标准 DNS 查询），**不读 hosts 文件、不支持 LLMNR/NetBIOS/mDNS**。`rxs` 靠 LLMNR 解析到 192.168.1.45，DNS 服务器无此记录 → `UnknownHostException: Failed to resolve 'rxs' [A(1)]`（NXDOMAIN）；而 JDBC/Nacos 用 JDK `InetAddress` 所以一直正常。**解法：定义 `ClientResources` bean 并设 `DnsResolvers.JVM_DEFAULT`**（Boot 自动配置会采用它）。service-auth 与 service-gateway 各有一份 `RedisLettuceConfig`。

## 鉴权架构（Redis Session Token，2026-09-20 定案）
- service-auth 登录：Feign 调 `verifyPassword` 校验密码 → 再调 `getUserByUsername` 取 userId/nickname → 组装 `AuthSession`（JSON）写 Redis + TTL → 返回 TokenInfoDTO。注销即 `DEL`，**令牌立即失效**。
- **网关不调 auth、不调任何校验接口**：`TokenAuthFilter` 直接读 Redis，0 次 RPC；auth 挂掉不影响已登录用户（已实测）。
- **契约里不应有 `validateToken` 这类接口**：它为「远程问票」而生，代价是 +1 RPC、可用性耦合、只回 Boolean 拿不到身份、还得额外做接口隔离。跨进程共享的是「Redis key + JSON 格式」，放在 `service-auth-api`：`AuthSession` / `AuthSessionKeys` / `AuthHeaders`。
- 网关策略：**默认拦截 + 白名单放行**（判据是「在不在白名单」，不是「有没有 `/api/` 前缀」）。白名单仅 `/api/auth/login`；logout 不在白名单（注销必须先持有效令牌）。`AntPathMatcher` 支持通配。401 必须带 `WWW-Authenticate: Bearer`（RFC 7235）。Redis 不可用回 **503 而非 401**（否则抖动会把在线用户全踢下线）。
- 身份透传：网关注入 `X-User-Id`/`X-Username`，**必须覆盖客户端自带的同名头**（追加会让下游 `getHeader` 拿到伪造值）；用 `HttpServletRequestWrapper` 实现。

## ⚠️ Sentinel 接入（SCA 2025.x 的坑，2026-09-20 实测）
- 依赖 `spring-cloud-starter-alibaba-sentinel`（版本 BOM 管理 → Sentinel 1.8.9）；Dashboard 见 `sentinel-dashboard.md`（rxs:8858，sentinel/sentinel）。
- **`@SentinelResource` 依赖 AOP，private 方法 + 类内自调用会静默失效**：受保护方法必须抽成独立 Spring Bean 的 public 方法，经代理调用才生效。
- **`sentinel-annotation-aspectj` 不自动注册切面**：必须手动 `@Bean SentinelResourceAspect`，否则注解静默失效（无任何报错）。
- **SCA 2025.x 移除了 `spring-cloud-alibaba-sentinel-feign` 模块**，`feign.sentinel.enabled` 老开关 + `@FeignClient(fallbackFactory=...)` 实测**静默不生效**（openfeign 5.0 的默认 builder 与之冲突）。**Feign 降级用 `@SentinelResource` 方式（抽独立 Bean）最可靠**。
- `fallback`（业务异常）vs `blockHandler`（被规则挡：限流/熔断，对应 `BlockException`）——签名都是「原方法参数 + 一个异常参数」，返回类型一致。
- Sentinel transport 端口(8719)是**懒加载**的，首次有请求经过 Sentinel 资源后才监听；下发规则 `GET http://127.0.0.1:8719/setRules?type=flow&data=<URL编码JSON>`（POST 会 415）。

## ⚠️ CORS 跨域（2026-09-21 定案 + 实测）
- **CORS 只在网关配置一处**（微服务惯例：网关是唯一外部出口，CORS 属横切关注点）。实测：网关与 service-order 同时开启时，**经网关的响应出现 2 个 `Access-Control-Allow-Origin`** → 浏览器报 `header contains multiple values` 直接拒绝（直连 8083 时 1 个，经网关时 2 个）。**只要两层都写这个头，就是错的。**
- **Boot 4 的 WebMVC 风味网关【没有】`globalcors` 配置项**（那是 WebFlux 风味才有的）。实测扫 `spring-cloud-gateway-server-webmvc` 5.0.x jar：**0 个 Cors 相关类、26 个配置属性里无一条 cors** → 网上 `spring.cloud.gateway.globalcors.cors-configurations` 那套教程照搬会**静默失效**。正解：注册 Servlet `CorsFilter`（`FilterRegistrationBean`，`order = Ordered.HIGHEST_PRECEDENCE`，先于 `TokenAuthFilter` 的 `HIGHEST_PRECEDENCE+10`）。
- `allowedOrigins("*")` 与 `allowCredentials(true)` **互斥**（Spring 直接抛异常）；必须用 `allowedOriginPatterns(List.of("*"))`——它会把请求 Origin **原样回显**，因此也覆盖 `file://` 页面发来的 `Origin: null`（实测 `ACAO: null` 正常返回）。
- 网关 `CorsFilter` 在过滤器链**之前**写头 → **401 响应也带 CORS 头**，浏览器能读到真实错误信息而非笼统的「CORS 错误」（实测通过）。而 `TokenAuthFilter` 的 `shouldNotFilter` 已放行 OPTIONS，预检不会被鉴权拦。
- service-order 保留一份 CORS 但**默认关闭**：`@ConditionalOnProperty(name="app.cors.direct-enabled", havingValue="true")`，仅供浏览器**直连 :8083 调试**（启动加 `--app.cors.direct-enabled=true`）。**切勿与网关的 CORS 同时开启。**
- 验证手法：用 `http.client` 的 `r.getheaders()` —— 它返回**保留重复项的原始头列表**；`urllib` 的 `dict(r.headers)` 会合并/丢失重复头，**测不出这个 bug**。

## 模块与契约约定
- 结构：`service-xxx`（实现）+ `service-xxx-api`（契约：DTO + `@FeignClient` 接口）。api 模块 openfeign 设 `<optional>true</optional>`，只引 springdoc `webmvc-api`；实现模块引 `webmvc-ui`。
- **契约接口四条铁律**：
  1. 带 `@FeignClient` 的接口上**绝不能有类级 `@RequestMapping`**（OpenFeign 5.0.0 `SpringMvcContract` 直接抛异常，启动失败）；路径前缀写在每个方法上，实现方 Controller 用 `implements` 继承。
  2. 同一 FeignClient name 下多个契约接口**必须指定不同 `contextId`**（OrderApi/ProductApi）。
  3. `@EnableFeignClients` **必须 `clients = XxxApi.class` 精确指定**，禁用 `basePackages` 宽扫描（会把本服务自己要实现的契约也生成代理 → `NoUniqueBeanDefinitionException`）。
  4. `@RequestBody` 必须用 Spring 的 `org.springframework.web.bind.annotation`（曾误用 swagger 的，导致请求体绑不进去）。
- 服务清单：service-system（sys_user，:8081，admin/admin123、zhangsan/123456）、service-auth（登录/会话，:8082，不连库）、service-order（订单+商品，:8083）、service-gateway（:8080）。
- 网关路由 4 条：`/api/sys-user/**`、`/api/auth/**`、`/api/order/**`、`/api/product/**`，uri 用 `lb://服务名`。

## 本机验证环境
- JDK `E:\Env\jdk-21.0.12.1`（需显式 `JAVA_HOME`）；Maven `E:\Env\apache-maven-3.9.16\bin\mvn.cmd`；本地仓库 `C:\Users\jiang\.m2\repository`；settings.xml 配代理可访问 Maven Central。
- **Bash 工具缺 coreutils（ls/grep/head/tail/dirname 全无），PowerShell 工具不回显 stdout**：查文件用 Read/Glob/Grep，列目录/跑脚本用 **Python**（`C:/Users/jiang/.workbuddy/binaries/python/versions/3.13.12/python.exe`）。**命令里不要用 `| tail`/`grep`。**
- **沙箱把 `server.port` 覆盖成随机端口**，启动必须显式加 `--server.port=8081`。给 java 传 `-Dfile.encoding=UTF-8` **必须加引号**。
- **YAML 列表无法用命令行参数局部追加**（`--a.routes[4].id=x` 会把整个列表覆盖成带空位的列表并绑定失败）。要临时加路由，用 `--spring.config.additional-location=file:<外部yml>` 给完整列表（模板 `.workbuddy/tmp/gateway-e2e.yml`）。
- 无 mysql/redis 客户端：Redis 用 Python 手写 RESP 客户端（`.workbuddy/tmp/redis_probe.py`）；MySQL 用 JDK 单文件模式 + 驱动。
- 可复用验证脚本：`.workbuddy/tmp/e2e_auth.py`（鉴权 23 项用例）、`.workbuddy/tmp/echo_server.py`（回显请求头，验证透传）。
- 日志重定向 `*>` 出 UTF-16 读不了，需 `Get-Content | Set-Content -Encoding UTF8`；Java 内直接以 UTF-8 写文件最省事。
- 手动重命名模块目录后要清理孤儿 `.iml` 与 `.idea`。
- **⚠️ Edit 工具不要对同一文件并发调用**：两次编辑都基于同一份旧内容写回，**后写的会静默覆盖先写的**。本次实际踩到两次——`service-order/CorsConfig.java` 丢了 `import ConditionalOnProperty`（编译才报错）、`tools/qps-tester.html` 丢了 `data()` 改动与 `login()` 方法（表面编辑全部"成功"）。**同一文件的多次修改必须串行，改完立刻用 Grep 核对落盘。**
