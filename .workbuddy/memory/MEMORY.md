# 项目长期记忆（springcloud-demo）

> 环境障碍与验证手法见 skill `local-microservice-e2e-verify`（缺 coreutils、`server.port` 被覆盖、YAML 列表无法命令行追加、Edit 同文件并发覆盖、日志编码、单文件模式跑单类压测；**netstat 返回空导致误判端口未监听、从工具里起的 java 会被进程树连带杀掉（必须用 WMI `Win32_Process.Create` 启动）、`spring-boot:repackage` 撞 jar 文件锁**）。此文件只记项目决策与铁律。

## 版本铁律（Boot 4.0.8 / Framework 7）
- 父 POM groupId=com.jwy，Boot **4.0.8**；Spring Cloud 必须 **2025.1.x** 列车（`spring-cloud-*` 5.0.x）。`2025.0.x` 编译于 Boot 4 里程碑版，不兼容（现象：网关 `NoClassDefFoundError: HttpRedirects`）。
- dependencyManagement 依次 import `spring-cloud-alibaba-dependencies:2025.1.0.0` + `spring-cloud-dependencies:2025.1.0`；**所有 spring-cloud-* 一律不写 `<version>`**。
- 网关用 `spring-cloud-starter-gateway-server-webmvc`（Boot 4 改名，旧 `spring-cloud-starter-gateway` 失效），配置根 `spring.cloud.gateway.server.webmvc.routes`，`lb://` 需另引 `spring-cloud-starter-loadbalancer`。
- **JSON 库是 Jackson 3（`tools.jackson.*`）**：注入 `com.fasterxml.jackson.databind.ObjectMapper` 会启动失败。
- MP 用 `mybatis-plus-spring-boot4-starter`；SpringDoc 用 **3.x**（2.8.x 是 Boot 3 线）。
- **YAML 列表只能用 `@ConfigurationProperties` 绑定**：`@Value("${xxx:}")` 静默取空值（踩过：网关白名单为空 → 连登录都 401，启动无报错）。
- **HTTP 头只能承载 ASCII**：中文昵称被发送端静默替换成 `?`，故网关只透传 `X-User-Id`/`X-Username`。
- 基础设施：MySQL 见 `mysql.md`（`sql.init.mode=always` 会 DROP+CREATE 清空真实库）；Nacos 见 `nacos.md`（rxs:8848，nacos/123456；**3.x 配置 API 是 `/nacos/v3/admin/cs/config`**，v1 已 404）；Redis rxs:6379 / **database 2** / 无密码 / 服务端 5.0.14（别用 6.2+ 命令），会话 key `auth:session:<token>`。
- **Lettuce 不读 hosts 文件**（用 Netty 解析器，只发标准 DNS），`rxs` 靠 LLMNR 解析 → `UnknownHostException`（JDBC/Nacos 走 JDK InetAddress 所以正常）。解法：`ClientResources` bean 设 `DnsResolvers.JVM_DEFAULT`（auth/gateway 各一份 `RedisLettuceConfig`）。

## 鉴权架构（Redis Session Token）
- auth 登录：Feign 校验密码 → 取 userId/nickname → `AuthSession`(JSON) 写 Redis+TTL → TokenInfoDTO。注销即 `DEL`，令牌立即失效。
- **网关不调 auth 也不调任何校验接口**：`TokenAuthFilter` 直读 Redis，0 次 RPC（auth 挂掉不影响已登录用户，已实测）。契约里**不应有 `validateToken`**；跨进程共享「Redis key + JSON 格式」，放在 `service-auth-api`（`AuthSession`/`AuthSessionKeys`/`AuthHeaders`）。
- 网关策略：**默认拦截 + 白名单放行**（判据是「在不在白名单」）；白名单仅 `/api/auth/login`，logout 不在内。401 必须带 `WWW-Authenticate: Bearer`；Redis 不可用回 **503 而非 401**（抖动不该把在线用户全踢下线）。
- 身份透传：网关注入 `X-User-Id`/`X-Username`，用 `HttpServletRequestWrapper` **覆盖**客户端同名头（追加会让下游拿到伪造值）。

## Sentinel（SCA 2025.x 的坑）
- **`@SentinelResource` 依赖 AOP**：受保护方法必须是**独立 Bean 的 public 方法**（private + 类内自调用静默失效）。
- **`sentinel-annotation-aspectj` 不自动注册切面**：必须手动 `@Bean SentinelResourceAspect`，否则静默失效。
- **SCA 2025.x 移除了 `spring-cloud-alibaba-sentinel-feign`**：`feign.sentinel.enabled` + `fallbackFactory` 实测**静默不生效** → Feign 降级一律用 `@SentinelResource`。
- `fallback`（业务异常）vs `blockHandler`（限流/熔断 `BlockException`）：签名都是「原方法参数 + 一个异常」，返回类型一致。
- transport 端口 8719 **懒加载**（首个请求经过资源后才监听）；下发规则 `GET http://127.0.0.1:8719/setRules?type=flow&data=<URL编码JSON>`（POST 会 415）。Dashboard 见 `sentinel-dashboard.md`（rxs:8858，sentinel/sentinel）。

## CORS
- **只在网关配置一处**（网关是唯一外部出口）。两层都写就错：实测经网关出现 **2 个 `Access-Control-Allow-Origin`** → 浏览器 `header contains multiple values` 直接拒绝。
- **Boot 4 的 WebMVC 风味网关【没有】`globalcors`**（扫 jar：0 个 Cors 类、26 个属性无 cors）→ 照搬 `spring.cloud.gateway.globalcors.*` 教程会**静默失效**。正解：Servlet `CorsFilter`（`FilterRegistrationBean`，`HIGHEST_PRECEDENCE`，先于 `TokenAuthFilter`）→ 401 响应也带 CORS 头。
- `allowedOrigins("*")` 与 `allowCredentials(true)` **互斥**（抛异常），必须用 `allowedOriginPatterns(List.of("*"))`（原样回显 Origin，覆盖 `file://` 的 `Origin: null`）。
- service-order 保留一份 CORS 但**默认关闭**（`app.cors.direct-enabled`），仅供浏览器直连 :8083 调试，**切勿与网关同开**。
- 验证重复响应头只能用 `http.client` 的 `r.getheaders()`；`urllib` 的 `dict(r.headers)` 会合并，测不出。

## 模块与契约
- 结构：`service-xxx`（实现）+ `service-xxx-api`（DTO + `@FeignClient`）。api 模块 openfeign 设 `<optional>true</optional>`，只引 springdoc `webmvc-api`；实现模块引 `webmvc-ui`。
- **契约四条铁律**：① 带 `@FeignClient` 的接口上**绝不能有类级 `@RequestMapping`**（OpenFeign 5.0 `SpringMvcContract` 直接抛异常）；② 同一 FeignClient name 下多接口必须指定不同 `contextId`；③ `@EnableFeignClients` 必须 `clients = XxxApi.class` 精确指定（`basePackages` 宽扫描 → `NoUniqueBeanDefinitionException`）；④ `@RequestBody` 必须用 Spring 注解（曾误用 swagger 的，请求体绑不进去）。
- 服务：service-system（sys_user，:8081，admin/admin123、zhangsan/123456）、service-auth（:8082，不连库）、service-order（订单+商品，:8083）、**service-id-gen（号段发号，:8084，可水平多实例，库 `leaf`）**、service-gateway（:8080）。网关 5 条路由：`/api/sys-user|auth|order|product|id/**` → `lb://服务名`。

## 订单号 / 业务单号
- **绝不用「时间戳 + 少量随机数」做唯一键**：同秒号码空间只有 1000 时，每秒 50 单冲突率 71%、100 单 99.4% → 压测必炸 `uk_order_no`（旧实现 `SO+秒级时间戳+3位随机` 的踩坑）。**且撞号在扣库存之后，每次回滚白跑一遍事务。**
- 正解（当前实现）：`service-order/support/OrderNoGenerator` = `SO + yyyyMMdd + 号段ID`，29 字符 / `VARCHAR(40)`，唯一性来自 service-id-gen 批发的号段（见下节）。**日期段只为人眼辨识，不承担唯一性**（号段按消费速度批发、不按天重置，跨天也不会重叠）。
- 写唯一键**兜一层换号重试**（`saveOrderWithUniqueNo`，最多 3 次后抛 503）。同一 `@Transactional` 内重试**安全**（MySQL 重复键只回滚该语句；`save()` 自调用、异常没过事务代理边界，不会被标 rollback-only）。
- **ID 生成策略现状（2026-09-21 清理完毕）**：① 所有表主键 —— `service-system` 的 4 个实体 + `service-order` 的 3 个实体，一律 `@TableId(type = IdType.AUTO)`，交给 MySQL 自增（`sys_user` 的 DDL 本来就是 `AUTO_INCREMENT`，此前被实体上的 `ASSIGN_ID` 注解**覆盖**了，所以 MP 一直绕过自增自己塞 ID）；② 唯一需要「对外可读 + 全局唯一」的订单号 `orderNo` 走号段（见下节）。**本项目已不再使用任何「时间戳 + 机器号」式的 64 位发号**：那类方案的机器号只能靠人工编排（环境变量 / K8s 序号）或租约申领分配，多实例每加一台就多一次人工配置，忘了或撞了就是**静默重号**；而 MP 默认的 `ASSIGN_ID` 在 `worker-id` 未显式配置时，机器号是按 `hash(MAC低位+PID)&0xffff % 32` 取模得到的 —— 总共只有 32 个取值且是**哈希落位而非分配**，2 实例撞车率 3.1%、5 个 28%、8 个 61%、15 个 98%（`pid < 10` 还会被判为容器改取随机数，拿不到网卡则退回固定 `(1,1)`）。号段模式的唯一性来自 MySQL 行锁，**与实例身份完全无关**。
- **不用 UUID 的理由**：订单号还兼「对外可读」「索引友好」——随机 UUID 插入点散落各索引页，36 字符、不含时间、人眼不可读。要不依赖协调，正解是**有序 UUID（v7/ULID）而非 v4**。
- **编号管理选型**：唯一性只能来自「全局编排」或「原子申领」，不能来自各实例各自推导。① 环境变量 / K8s StatefulSet 序号（最省事）；② 租约式申领（MySQL `UPDATE ... WHERE heartbeat_at < NOW(3)-INTERVAL 60 SECOND` 影响行数=1 才算抢到，20s 续租，**续租失败必须退出进程**，抢不到号 fail-fast）；③ 号段（领一段本地发）。参考：美团 Leaf（**ZK 顺序节点**发号）、百度 uid-generator、滴滴 Tinyid。**Nacos 不能当发号器**——它解决「分发」不是「分配」；按实例列表顺序推编号在扩缩容窗口内必然撞号。
- **Leaf-segment 事实核查**（2026-09 已 fetch 源码）：坐标 `com.sankuai.inf.leaf:leaf-core:1.0.1`，**不在 Maven 中央仓库**（须 clone + 本地 install）；`leaf-core` 的 `spring-*` **全是 test scope** → 它**不依赖 Spring**；HTTP 只有单发 `getId`，**无取段接口**；核心代码 2018 年后基本冻结。**官方无 Docker 版本/无官方镜像**；`leaf-server` 自带 HTTP（`/api/segment/get/{key}`、`/api/snowflake/get/{key}`、`/cache` 监控页，两模式**默认 false**）但**只发单个 ID**，且官方 5w/s 是 **RPC** 压的。**step 官方推荐 = 高峰发号 QPS × 600（≈10 分钟容灾）**；双 buffer 触发点是**已下发 10%**（美团设计文档原话）。官方承认三缺点：TP999 尖刺、**DB 挂则整体不可用**（容灾押在 DB：一主两从+半同步+DBProxy）、**ID 连续会泄露单量**。**取段正解（Leaf 原实现）**：同一事务内 `update max_id=max_id+step` + `select max_id`，**commit 前不放行锁** → 读到的必是自己刚写的值。**号段权威源必须是"提交成功才算数"的存储**：Redis `INCRBY` 有主从异步复制/AOF everysec 丢尾 → 号段回退 → 重号，故用 MySQL。
- **⚠️ 待办：订单接口缺归属校验（IDOR）**：service-order 从不读网关注入的 `X-User-Id`，且 `GET /api/order/{id}` 走自增 id → 任意登录用户可枚举读取/取消/删除他人订单。**号段 ID 连续可枚举会加重这条**（连猜都不必，+1 即可，还能靠号差反推单量）——美团正因如此对外订单号另用不可预测的算法、号段只做内部主键；真要对外可得做 Feistel/乘加可逆混淆或让订单号不进对外 API，但这**替代不了归属校验**。

## 分布式发号：service-id-gen（自实现 Leaf-segment）
- 结构：`service-id-gen-api`（契约 + **号段消费组件**）+ `service-id-gen`（实现，:8084）。
- **状态机放 api 模块**（同 Leaf 把 SegmentBuffer 放 leaf-core）：`Segment`（`PREFETCH_RATIO=0.10`）、`SegmentBuffer`（双 buffer + CAS **单飞预取** + 失败冷却 200ms + 段尽抛 `SegmentUnavailableException`）、`SegmentSupplier`（函数式取段：服务端=MySQL、客户端=Feign）。服务端与业务端共用同一套，只有取段来源不同。
- 取段在 `LeafAllocator`：**同一事务内先 `UPDATE leaf_alloc SET max_id = max_id + step WHERE biz_tag=?`（影响行数须为 1），再 `SELECT max_id`** —— InnoDB 行锁仲裁并发，各实例区间必然不重叠。**别**先 SELECT 再算再 UPDATE（真重号），**别**用 `LAST_INSERT_ID`（连接级状态，连接池下不可靠）。
- `SegmentIdGen.bufferOf()` 用「先读再锁内复查」，**刻意不用 `computeIfAbsent`**：那会在持有 ConcurrentHashMap 桶锁时做 DB 调用。
- 段尽且取不到新段 → **503，绝不降级为本地自增**（那是跨实例重号）。`GlobalExceptionHandler` 把 `SegmentUnavailableException`/`DataAccessException` 收敛成 503。
- 业务端 `service-order/support/SegmentIdGenerator` 持有 buffer（Feign 领段），启动预热靠 **`IdGenWarmUpRunner`（ApplicationRunner；`app.id-gen.warm-up-on-startup` 默认开）**。
- 接口：`/api/id/next`（单发，走服务端 buffer）、`/api/id/segment`（领段，**无状态直连 DB**，业务端用这个）、`/api/id/status`（双 buffer 状态）。
- **bizTag 约定**：业务用 `order`（step=100000，官方口径=高峰QPS×600）；**`demo` 专供压测/演示**（step=2000）—— 测多实例唯一性要重置 max_id，拿 `order` 去压会让新号与已落库订单号重叠、被唯一键拒单。step 存库里、取段现读，可热调不需重启。
- **唯一性域就是 `biz_tag` 本身（2026-09-21 澄清）**：`leaf_alloc` 各行是独立账本，`max_id` **跨行无任何约束**（两个 tag 停在同一个数完全合法，`step` 也允许各不相同）。但**不同 biz_tag 的号码空间完全重叠** —— 每个 tag 都从 0 起独立批发，现状 `order` 已批到 1914000 **⊇** `demo` 已批到 120000，即 demo 发出的每个号，order 都能发出同一个数字。推论：① 同一个 ID 语义**终身只用一个 tag**，换 tag = 换号码空间 = 造重号（真要换只能靠前缀/日期段区分开）；② 反过来**多个业务共用一个 tag 反而是安全的**（同一序列的子集，天然不重），代价是互相消耗号段、step 只能取折中值 —— 保守原则是「能复用就复用，新建 tag 才要论证」；③ `max_id` **不能当发号计数**，在途未消费的号已从它里面扣走。对照：把「时间戳 + 机器号」编进 64 位的发号方案是**全局唯一**（不依赖 tag），号段则天生是**分域唯一** —— 这就是它换到「发号不依赖实例身份」所付出的代价。
- **`warmUp()` 的日志会误导**：`tryPrefetch()` 是**异步**提交的，`[id-gen] 预热完成：... next=null` 打在异步任务完成之前，看着像预热没生效。查真实状态要用 `/api/id/status` 的 `nextReady`（实测 `true`）。
- 已实测的收益：**停掉全部 id-gen 实例后，在途号段内下单照样 200** —— 发号不在下单关键路径上。

## 本机验证环境
- JDK `E:\Env\jdk-21.0.12.1`（须显式 `JAVA_HOME`）；Maven `E:\Env\apache-maven-3.9.16\bin\mvn.cmd`；仓库 `C:\Users\jiang\.m2\repository`；Python `C:/Users/jiang/.workbuddy/binaries/python/versions/3.13.12/python.exe`。
- `mvn -pl <module>` **必须带 `-am`**（api 兄弟模块没 install，否则依赖解析失败），编译日志要确认出现 `Compiling N source files`。**重建前先停服务**（repackage 撞文件锁）。
- 可复用脚本（`.workbuddy/tmp/`）：**`svc.py`（WMI 启停 + socket 探测 + 日志解码，首选）**、**`id_gen_e2e.py`（发号 12 项，`--biz demo --urls 8084,8085`）**、**`order_e2e.py`（下单三阶段 12 项，含停 id-gen 验证本地号段续命与 503 红线）**、`e2e_auth.py`（鉴权 23 项）、`echo_server.py`（回显请求头验透传）、`redis_probe.py`（手写 RESP）、`Sql.java`（单文件 SQL）、`OrderNoStressTest.java`（订单号并发压测对比）、`append_block.py`（追加只增不改的日志）。
