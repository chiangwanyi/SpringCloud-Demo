# 项目长期记忆（springcloud-demo）

> 环境障碍与验证套路已沉淀到 skill `local-microservice-e2e-verify`（缺 coreutils、`server.port` 被覆盖、YAML 列表无法命令行追加、Edit 同文件并发覆盖、日志编码、单文件模式跑单类压测等）。此文件只记项目本身的决策与铁律。

## 版本铁律（Boot 4.0.8 / Framework 7）
- 父 POM groupId=com.jwy，Boot **4.0.8**；Spring Cloud 必须 **2025.1.x** 列车（`spring-cloud-*` 5.0.x）。`2025.0.x` 编译于 Boot 4 里程碑版，**不兼容**（现象：网关 `NoClassDefFoundError: HttpRedirects`）。
- dependencyManagement 依次 import `spring-cloud-alibaba-dependencies:2025.1.0.0` + `spring-cloud-dependencies:2025.1.0`；**所有 spring-cloud-* 一律不写 `<version>`**。
- 网关用 `spring-cloud-starter-gateway-server-webmvc`（Boot 4 改名，旧 `spring-cloud-starter-gateway` 失效），配置根 `spring.cloud.gateway.server.webmvc.routes`，`lb://` 需另引 `spring-cloud-starter-loadbalancer`。
- **JSON 库是 Jackson 3（`tools.jackson.*`）**：注入 `com.fasterxml.jackson.databind.ObjectMapper` 会启动失败。
- MyBatis-Plus 用 `mybatis-plus-spring-boot4-starter`；API 文档用 SpringDoc **3.x**（2.8.x 是 Boot 3 线）。
- **YAML 列表只能用 `@ConfigurationProperties` 绑定**：`@Value("${xxx:}")` 会静默取默认空值（踩过：网关白名单为空 → 连登录都 401，且启动无任何报错）。
- **HTTP 头只能承载 ASCII**：中文昵称会被发送端静默替换成 `?`；故网关只透传 `X-User-Id`/`X-Username`。
- 基础设施：MySQL 见 `mysql.md`（`sql.init.mode=always` 启动会 DROP+CREATE 清空真实库）；Nacos 见 `nacos.md`（rxs:8848，nacos/123456；**3.x 配置 API 是 `/nacos/v3/admin/cs/config`**，v1 已 404）；Redis rxs:6379 / **database 2** / 无密码 / 服务端 5.0.14（别用 6.2+ 命令），会话 key `auth:session:<token>`。
- **Lettuce 不读 hosts 文件**：它用 Netty 自带解析器（只发标准 DNS），而 `rxs` 靠 LLMNR 解析 → `UnknownHostException`（JDBC/Nacos 用 JDK InetAddress 所以正常）。解法：定义 `ClientResources` bean 设 `DnsResolvers.JVM_DEFAULT`（auth 与 gateway 各一份 `RedisLettuceConfig`）。

## 鉴权架构（Redis Session Token）
- auth 登录：Feign 校验密码 → 再取 userId/nickname → 组装 `AuthSession`（JSON）写 Redis + TTL → 返回 TokenInfoDTO。注销即 `DEL`，**令牌立即失效**。
- **网关不调 auth、不调任何校验接口**：`TokenAuthFilter` 直接读 Redis，0 次 RPC（auth 挂掉不影响已登录用户，已实测）。契约里**不应有 `validateToken` 这类接口**；跨进程共享的是「Redis key + JSON 格式」，放在 `service-auth-api`（`AuthSession`/`AuthSessionKeys`/`AuthHeaders`）。
- 网关策略：**默认拦截 + 白名单放行**（判据是「在不在白名单」，不是「有没有 `/api/` 前缀」）；白名单仅 `/api/auth/login`，logout 不在白名单。401 必须带 `WWW-Authenticate: Bearer`；Redis 不可用回 **503 而非 401**（否则抖动会把在线用户全踢下线）。
- 身份透传：网关注入 `X-User-Id`/`X-Username`，用 `HttpServletRequestWrapper` **覆盖**客户端自带的同名头（追加会让下游 `getHeader` 拿到伪造值）。

## Sentinel（SCA 2025.x 的坑）
- **`@SentinelResource` 依赖 AOP**：受保护方法必须是**独立 Bean 的 public 方法**（private + 类内自调用静默失效）。
- **`sentinel-annotation-aspectj` 不自动注册切面**：必须手动 `@Bean SentinelResourceAspect`，否则注解静默失效、无报错。
- **SCA 2025.x 移除了 `spring-cloud-alibaba-sentinel-feign`**：`feign.sentinel.enabled` + `@FeignClient(fallbackFactory=...)` 实测**静默不生效** → **Feign 降级一律用 `@SentinelResource`（抽独立 Bean）**。
- `fallback`（业务异常）vs `blockHandler`（被限流/熔断挡，`BlockException`）：签名都是「原方法参数 + 一个异常」，返回类型一致。
- transport 端口(8719)**懒加载**（首次有请求经过资源后才监听）；下发规则 `GET http://127.0.0.1:8719/setRules?type=flow&data=<URL编码JSON>`（POST 会 415）。Dashboard 见 `sentinel-dashboard.md`（rxs:8858，sentinel/sentinel）。

## CORS 跨域
- **只在网关配置一处**（网关是唯一外部出口）。两层都写就是错的：实测经网关时出现 **2 个 `Access-Control-Allow-Origin`** → 浏览器报 `header contains multiple values` 直接拒绝。
- **Boot 4 的 WebMVC 风味网关【没有】`globalcors` 配置项**（扫 jar：0 个 Cors 类、26 个属性无一条 cors）→ 网上 `spring.cloud.gateway.globalcors.*` 教程照搬会**静默失效**。正解：注册 Servlet `CorsFilter`（`FilterRegistrationBean`，`HIGHEST_PRECEDENCE`，先于 `TokenAuthFilter`）→ **401 响应也带 CORS 头**。
- `allowedOrigins("*")` 与 `allowCredentials(true)` **互斥**（抛异常），必须用 `allowedOriginPatterns(List.of("*"))`——它把请求 Origin **原样回显**，因此也覆盖 `file://` 的 `Origin: null`。
- service-order 保留一份 CORS 但**默认关闭**（`app.cors.direct-enabled`），仅供浏览器直连 :8083 调试，**切勿与网关同开**。
- 验证重复响应头只能用 `http.client` 的 `r.getheaders()`（保留重复项的原始列表）；`urllib` 的 `dict(r.headers)` 会合并，测不出这个 bug。

## 模块与契约约定
- 结构：`service-xxx`（实现）+ `service-xxx-api`（契约：DTO + `@FeignClient` 接口）。api 模块 openfeign 设 `<optional>true</optional>`，只引 springdoc `webmvc-api`；实现模块引 `webmvc-ui`。
- **契约四条铁律**：① 带 `@FeignClient` 的接口上**绝不能有类级 `@RequestMapping`**（OpenFeign 5.0 `SpringMvcContract` 直接抛异常）；② 同一 FeignClient name 下多个契约接口必须指定不同 `contextId`；③ `@EnableFeignClients` 必须 `clients = XxxApi.class` 精确指定（`basePackages` 宽扫描 → `NoUniqueBeanDefinitionException`）；④ `@RequestBody` 必须用 Spring 的注解（曾误用 swagger 的，请求体绑不进去）。
- 服务：service-system（sys_user，:8081，admin/admin123、zhangsan/123456）、service-auth（登录/会话，:8082，不连库）、service-order（订单+商品，:8083）、service-gateway（:8080）。网关路由 4 条：`/api/sys-user|auth|order|product/**` → `lb://服务名`。

## 订单号 / 业务单号
- **绝不用「时间戳 + 少量随机数」做唯一键**：旧实现 `SO + 秒级时间戳 + 3 位随机`，同一秒只有 1000 个号码空间 —— 每秒 50 单冲突率 71%、100 单 99.4%、200 单 100% → 压测必炸 `DuplicateKeyException uk_order_no`。
- 正解：`service-order/support/OrderNoGenerator` = `SO + yyyyMMdd + IdWorker.getIdStr()`（MP 内置雪花），29 字符 / `VARCHAR(40)`。**日期段只为人眼辨识，唯一性由雪花承担（职责分离）。**
- 写唯一键要**兜一层换号重试**（`saveOrderWithUniqueNo`，最多 3 次后抛 503）。同一 `@Transactional` 内重试是**安全**的（MySQL 重复键只回滚该语句；`save()` 自调用、异常没穿过事务代理边界，不会被标 rollback-only）。
- **MP 雪花在多实例下不可靠**：机器号 = (MAC+PID) 哈希取低 16 位再模 32，同机只有 32 个值、约 1/32 撞车概率。生产须 `IdWorker.initSequence(workerId, dataCenterId)` 显式指定。实测单 JVM 纯生成 26 万/秒，瓶颈不在生成器。
- **为什么不用 UUID**：订单号还兼着「对外可读」和「索引友好」两个职责 —— 随机 UUID 的插入点散落各索引页（多一次读页 + 页利用率低 + 索引膨胀），且 32~36 字符、不含时间、人眼与客服不可读。要不依赖协调，正解是**有序 UUID（v7/ULID）而非 v4**。
- **⚠️ 待办：订单接口缺归属校验（IDOR）**：service-order 从不读网关注入的 `X-User-Id`，且 `GET /api/order/{id}` 走自增 id → 任意登录用户可枚举读取/取消/删除他人订单。UUID 只能防遍历，替代不了归属校验。

## 本机验证环境
- JDK `E:\Env\jdk-21.0.12.1`（须显式 `JAVA_HOME`）；Maven `E:\Env\apache-maven-3.9.16\bin\mvn.cmd`；本地仓库 `C:\Users\jiang\.m2\repository`；Python `C:/Users/jiang/.workbuddy/binaries/python/versions/3.13.12/python.exe`。
- 可复用脚本（`.workbuddy/tmp/`）：`e2e_auth.py`（鉴权 23 项用例）、`echo_server.py`（回显请求头验透传）、`redis_probe.py`（手写 RESP）、`OrderNoStressTest.java`（订单号并发压测，对比新旧实现）。
- `mvn -pl <module>` **必须带 `-am`**：api 兄弟模块没 install 到本地仓库，否则依赖解析失败。
