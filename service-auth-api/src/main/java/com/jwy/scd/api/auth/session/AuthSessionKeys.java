package com.jwy.scd.api.auth.session;

/**
 * Redis 会话存储的 key 约定 —— service-auth（写入方）与 service-gateway（读取方）共用。
 *
 * <p>把它放在 api 契约模块而不是各服务本地常量，是为了<b>从根上杜绝 key 前缀不一致</b>：
 * 一旦两侧前缀写错一个字（比如一边 {@code auth:session:}、一边 {@code auth:sessions:}），
 * 表现就是「登录成功但所有请求都 401」，且不会有任何异常日志，极难排查。共用一个常量类就不可能出现。
 *
 * <p><b>存储结构</b>：
 * <pre>
 *   key   : auth:session:3f2a1b9c8d7e6f5a4b3c2d1e0f9a8b7c   （String 类型）
 *   value : {"token":"3f2a...","userId":2,"username":"zhangsan","nickname":"张三","loginTime":1789000000000}
 *   TTL   : 见两侧 app.auth.session-timeout 配置（默认 1800 秒）
 * </pre>
 * 令牌是否存在，等价于「会话是否有效」；TTL 到期后 Redis 自动删除 key，令牌随之失效 —— 无需任何清理任务。
 *
 * <p>用 {@code redis-cli} 查看（本项目库号 2）：
 * <pre>
 *   redis-cli -h rxs -p 6379 -n 2 KEYS 'auth:session:*'
 *   redis-cli -h rxs -p 6379 -n 2 GET  'auth:session:&lt;token&gt;'
 *   redis-cli -h rxs -p 6379 -n 2 TTL  'auth:session:&lt;token&gt;'
 * </pre>
 */
public final class AuthSessionKeys {

    /**
     * 会话 key 前缀。
     *
     * <p>命名规范：{@code 业务:实体:} 两级冒号分隔。加业务前缀的原因是可读性与隔离性 ——
     * 同一个 Redis 库（本项目 db2）将来还会放验证码、接口限流计数、缓存等数据，
     * 统一的 {@code auth:} 前缀既方便 {@code KEYS auth:session:*} 批量排查，
     * 也方便按前缀做权限隔离（例如给不同业务分配不同的 Redis ACL 用户）。
     */
    public static final String SESSION_KEY_PREFIX = "auth:session:";

    /** 工具类，禁止实例化 */
    private AuthSessionKeys() {
    }

    /**
     * 由令牌字符串拼出 Redis 会话 key。
     *
     * @param token 令牌字符串（登录时签发）
     * @return 形如 {@code auth:session:&lt;token&gt;} 的 key
     */
    public static String sessionKey(String token) {
        return SESSION_KEY_PREFIX + token;
    }
}
