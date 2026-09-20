package com.jwy.scd.api.auth;

/**
 * 认证相关的 HTTP 头约定 —— 由 service-auth / service-gateway 共用，下游业务服务读取。
 *
 * <p>为什么这些常量也要放进契约模块？因为它们同样是「跨服务约定」：
 * <ol>
 *     <li>{@link #AUTHORIZATION_PREFIX}：客户端与网关、认证服务三方对「Bearer 令牌」格式的理解必须一致；</li>
 *     <li>{@link #USER_ID} / {@link #USERNAME}：
 *         <b>网关注入（写方）</b>与<b>下游业务服务读取（读方）</b>必须用完全相同的头名。
 *         写错一个字母的后果是下游永远读到 null，且不会报错。</li>
 * </ol>
 * 把这些字面量集中在一处，就不会出现「网关写 {@code X-UserId}、下游读 {@code X-User-Id}」这类事故。
 *
 * <p><b>⚠️ 这里刻意没有「用户昵称」头 —— 这是一个实测踩过的坑，务必理解。</b>
 * 最初的版本额外注入了 {@code X-User-Nickname}，把会话里的昵称（如「张三」）透传给下游。
 * 端到端测试发现下游收到的值是 <b>{@code "??"}</b>（不是乱码，而是被替换成了问号）。
 * 原因是 <b>HTTP 头按规范只能承载 ASCII（ISO-8859-1）</b>：
 * 发送端（JDK HttpClient）在写头部时会把无法用 ISO-8859-1 表示的字符直接替换成 {@code '?'}，
 * 这是静默的数据损坏，不报错、不抛异常，只在收到时才发现值没了。
 *
 * <p>所以约定是：<b>请求头里只放 ASCII 安全的标识信息（ID、登录名）</b>。
 * 下游如果需要昵称这类可能含中文的展示信息，用 {@link #USER_ID} 回查用户中心
 * （或者由网关把它写进请求体 —— 但那是另一套设计，会污染业务模型，不推荐）。
 * 若确实必须在头里传非 ASCII 文本，唯一正确的做法是做百分号编码 / Base64 并在下游显式解码，
 * 而不是直接把原文塞进去。
 */
public final class AuthHeaders {

    /** Bearer 令牌前缀（注意末尾有一个空格） */
    public static final String AUTHORIZATION_PREFIX = "Bearer ";

    /**
     * 用户 ID 请求头。由网关在校验会话通过后注入，值为 {@code AuthSession.userId}。
     *
     * <p><b>⚠️ 下游服务只能信任「来自网关」的这个头。</b>网关在注入前会先剥离客户端
     * 自带的同名头，否则任何人都能伪造 {@code X-User-Id: 1} 冒充管理员。
     * 因此下游服务要保证 <b>只能通过网关访问</b>（不要把业务端口直接暴露到公网）。
     */
    public static final String USER_ID = "X-User-Id";

    /**
     * 用户名请求头，由网关注入，值为 {@code AuthSession.username}。
     *
     * <p>约定登录名必须是 ASCII（字母/数字/下划线等）。若允许用中文当登录名，
     * 这个头同样会被替换成问号 —— 原因见类注释。
     */
    public static final String USERNAME = "X-Username";

    private AuthHeaders() {
    }

    /**
     * 从 {@code Authorization} 头中解析出令牌本体。
     *
     * <p>两侧共用同一份解析逻辑，避免「网关认 {@code bearer} 小写、认证服务只认 {@code Bearer}」
     * 这种不一致。解析失败统一返回 {@code null}，由调用方决定怎么处理（网关 401、认证服务忽略）。
     *
     * @param authorizationHeader 形如 {@code Bearer 3f2a1b9c...} 的头部值，可以为 null
     * @return 令牌字符串；头部为空或格式不符时返回 {@code null}
     */
    public static String resolveToken(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }
        String value = authorizationHeader.trim();
        if (value.regionMatches(true, 0, AUTHORIZATION_PREFIX, 0, AUTHORIZATION_PREFIX.length())) {
            String token = value.substring(AUTHORIZATION_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
