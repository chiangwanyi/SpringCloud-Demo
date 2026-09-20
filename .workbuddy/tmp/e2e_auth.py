"""端到端验证：Redis Session Token 鉴权链路。

验证项：
  1  无令牌访问业务接口 -> 401 + WWW-Authenticate: Bearer + problem+json
  2  伪造令牌 -> 401
  3  非白名单路径（不带 /api/ 前缀）-> 401（默认拦截）
  4  错误密码登录 -> 失败，且不产生会话
  5  正确密码登录 -> 200 + 令牌
  6  Redis(db2) 中确实写入了 auth:session:<token>，且内容含 userId/username/nickname/TTL
  7  携带令牌访问业务接口 -> 200
  8  身份头透传：下游回显服务能看到 X-User-Id / X-Username / X-User-Nickname
  9  防伪造：客户端伪造的 X-User-Id 被网关覆盖
 10  登出 -> 200，且 Redis 会话被删除
 11  登出后的令牌 -> 401（即时吊销）
"""
import json
import sys
import os
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from redis_probe import MiniRedis  # noqa: E402

GATEWAY = "http://127.0.0.1:8080"
REDIS_HOST = "rxs"
REDIS_PORT = 6379
REDIS_DB = 2

results = []


def call(method, url, body=None, headers=None, timeout=15):
    data = None
    hdrs = dict(headers or {})
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        hdrs["Content-Type"] = "application/json; charset=utf-8"
    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", "replace")
            return resp.status, dict(resp.headers), raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        return e.code, dict(e.headers), raw


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print("[%s] %s%s" % ("PASS" if ok else "FAIL", name, ("  -> " + detail) if detail else ""))


def main():
    r = MiniRedis(REDIS_HOST, REDIS_PORT, REDIS_DB)
    # 清掉可能残留的会话，保证测试从干净状态开始
    for k in r.cmd("KEYS", "auth:session:*"):
        r.cmd("DEL", k)

    # ---------- 1. 无令牌 ----------
    st, hd, body = call("GET", GATEWAY + "/api/sys-user/list")
    check("1 无令牌访问 -> 401", st == 401, "status=%s" % st)
    check("1a 带 WWW-Authenticate: Bearer", hd.get("WWW-Authenticate") == "Bearer", str(hd.get("WWW-Authenticate")))
    check("1b Content-Type 为 problem+json",
          "problem+json" in (hd.get("Content-Type") or ""), str(hd.get("Content-Type")))
    check("1c 响应体为 RFC7807", '"status":401' in body.replace(" ", ""), body.strip()[:120])

    # ---------- 2. 伪造令牌 ----------
    st, _, _ = call("GET", GATEWAY + "/api/sys-user/list", headers={"Authorization": "Bearer not-a-real-token"})
    check("2 伪造令牌 -> 401", st == 401, "status=%s" % st)

    # ---------- 3. 非白名单且无 /api/ 前缀的路径 ----------
    st, _, _ = call("GET", GATEWAY + "/internal/secret")
    check("3 非白名单路径 -> 401（默认拦截）", st == 401, "status=%s" % st)

    # ---------- 4. 错误密码 ----------
    st, _, body = call("POST", GATEWAY + "/api/auth/login", {"username": "zhangsan", "password": "wrong-pwd"})
    check("4 错误密码登录 -> 非 2xx", st >= 400, "status=%s body=%s" % (st, body.strip()[:120]))
    check("4a 未产生任何会话", r.cmd("KEYS", "auth:session:*") == [], "keys=%s" % r.cmd("KEYS", "auth:session:*"))

    # ---------- 5. 正确密码 ----------
    st, _, body = call("POST", GATEWAY + "/api/auth/login", {"username": "zhangsan", "password": "123456"})
    check("5 正确密码登录 -> 200", st == 200, "status=%s body=%s" % (st, body.strip()[:200]))
    if st != 200:
        summary()
        return
    info = json.loads(body)
    token = info["token"]
    check("5a 返回 userId/nickname（来自用户中心）",
          info.get("userId") == 2 and info.get("nickname") == "张三",
          "userId=%s nickname=%s" % (info.get("userId"), info.get("nickname")))
    check("5b expiresIn 为 1800", info.get("expiresIn") == 1800, str(info.get("expiresIn")))

    # ---------- 6. Redis 会话 ----------
    key = "auth:session:" + token
    raw = r.cmd("GET", key)
    check("6 Redis 中存在会话 key", raw is not None, key)
    if raw:
        sess = json.loads(raw)
        check("6a 会话 JSON 字段完整",
              sess.get("userId") == 2 and sess.get("username") == "zhangsan" and sess.get("nickname") == "张三"
              and isinstance(sess.get("loginTime"), int),
              raw[:200])
        ttl = r.cmd("TTL", key)
        check("6b TTL 已设置", isinstance(ttl, int) and 0 < ttl <= 1800, "ttl=%s" % ttl)

    auth = {"Authorization": "Bearer " + token}

    # ---------- 7. 带令牌访问业务接口 ----------
    st, _, body = call("GET", GATEWAY + "/api/sys-user/list", headers=auth)
    check("7 带令牌访问业务接口 -> 200", st == 200, "status=%s body=%s" % (st, body.strip()[:120]))

    # ---------- 8. 身份头透传（经网关临时路由打到回显服务） ----------
    st, _, body = call("GET", GATEWAY + "/api/echo/probe", headers=auth)
    if st == 200:
        echo = json.loads(body)
        got = echo["headerValues"]
        check("8 X-User-Id 透传到下游", got.get("X-User-Id") == ["2"], str(got.get("X-User-Id")))
        check("8a X-Username 透传到下游", got.get("X-Username") == ["zhangsan"], str(got.get("X-Username")))
        # HTTP 头只能承载 ASCII；中文昵称（张三）放进头里会被发送端静默替换成 "??"，
        # 因此设计上刻意不注入昵称头 —— 这里断言它确实不存在，防止有人"好心"加回去。
        check("8b 昵称未放进请求头（HTTP 头只能 ASCII）",
              got.get("X-User-Nickname") is None, str(got.get("X-User-Nickname")))
    else:
        check("8 身份头透传（回显服务未就绪）", False, "status=%s" % st)

    # ---------- 9. 防伪造 ----------
    st, _, body = call("GET", GATEWAY + "/api/echo/probe",
                       headers={**auth, "X-User-Id": "1", "x-username": "admin"})
    if st == 200:
        got = json.loads(body)["headerValues"]
        check("9 伪造 X-User-Id 被覆盖", got.get("X-User-Id") == ["2"], str(got.get("X-User-Id")))
        check("9a 伪造 x-username 被覆盖", got.get("X-Username") == ["zhangsan"], str(got.get("X-Username")))
    else:
        check("9 防伪造（回显服务未就绪）", False, "status=%s" % st)

    # ---------- 10. 登出 ----------
    st, _, body = call("POST", GATEWAY + "/api/auth/logout", headers=auth)
    check("10 登出 -> 200", st == 200, "status=%s body=%s" % (st, body.strip()[:120]))
    check("10a Redis 会话被删除", r.cmd("EXISTS", key) == 0, key)

    # ---------- 11. 吊销后即时失效 ----------
    st, _, _ = call("GET", GATEWAY + "/api/sys-user/list", headers=auth)
    check("11 登出后令牌立即失效 -> 401", st == 401, "status=%s" % st)

    r.close()
    summary()


def summary():
    failed = [n for n, ok, _ in results if not ok]
    print("\n==== 合计 %d 项，通过 %d，失败 %d ====" % (len(results), len(results) - len(failed), len(failed)))
    if failed:
        print("失败项：" + " | ".join(failed))
        sys.exit(1)
    print("E2E_ALL_PASS")


if __name__ == "__main__":
    main()
