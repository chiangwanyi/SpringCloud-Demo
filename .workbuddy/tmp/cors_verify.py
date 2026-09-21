"""CORS 端到端验证脚本。

用 http.client 而非 urllib，因为需要拿到「原始响应头列表」（保留重复项），
才能验证有没有出现两个 Access-Control-Allow-Origin（浏览器会因此拒绝）。
"""
import http.client
import json
import sys

TOOL_ORIGIN = "http://127.0.0.1:8090"


def req(port, method, path, headers=None, body=None, host="127.0.0.1"):
    c = http.client.HTTPConnection(host, port, timeout=20)
    try:
        c.request(method, path, body=body, headers=headers or {})
        r = c.getresponse()
        data = r.read()
        hdrs = r.getheaders()
        return r.status, hdrs, data
    finally:
        c.close()


def report(title, status, hdrs, data, expect_body=False):
    cors = [(k, v) for k, v in hdrs if k.lower().startswith("access-control")]
    acao = [v for k, v in hdrs if k.lower() == "access-control-allow-origin"]
    print("=" * 72)
    print(title)
    print("  HTTP status :", status)
    print("  ACAO 出现次数:", len(acao), acao)
    for k, v in cors:
        print("    %-38s = %s" % (k, v))
    if expect_body:
        try:
            print("  body:", data.decode("utf-8", "replace")[:200])
        except Exception:
            pass
    print()


print("\n########## 一、直连 service-order :8083 ##########\n")

# A. 预检请求（OPTIONS）—— 浏览器发实际请求前的"试探"
s, h, d = req(8083, "OPTIONS", "/api/order", {
    "Origin": TOOL_ORIGIN,
    "Access-Control-Request-Method": "POST",
    "Access-Control-Request-Headers": "content-type,authorization",
})
report("A. 预检 OPTIONS http://127.0.0.1:8083/api/order  (Origin=%s)" % TOOL_ORIGIN, s, h, d)

# B. 实际请求带上 Origin
payload = json.dumps({"userId": 2, "items": [{"productId": 1, "quantity": 1}]}).encode()
s, h, d = req(8083, "POST", "/api/order", {
    "Origin": TOOL_ORIGIN,
    "Content-Type": "application/json",
}, payload, )
report("B. 实际 POST http://127.0.0.1:8083/api/order  (带 Origin)", s, h, d, expect_body=True)

# C. file:// 页面发来的请求，Origin 是字符串 "null"
s, h, d = req(8083, "POST", "/api/order", {
    "Origin": "null",
    "Content-Type": "application/json",
}, payload)
report("C. 实际 POST :8083  (Origin=null，模拟 file:// 打开的本地 HTML)", s, h, d)


print("########## 二、经网关 service-gateway :8080 ##########\n")

# D. 网关上的预检
s, h, d = req(8080, "OPTIONS", "/api/order", {
    "Origin": TOOL_ORIGIN,
    "Access-Control-Request-Method": "POST",
    "Access-Control-Request-Headers": "content-type,authorization",
})
report("D. 预检 OPTIONS http://127.0.0.1:8080/api/order  (Origin=%s)" % TOOL_ORIGIN, s, h, d)

# E. 无令牌的业务请求：应当 401，但响应仍必须带 CORS 头，
#    否则浏览器读不到 401 的具体原因，只会显示含糊的 "CORS error"
s, h, d = req(8080, "POST", "/api/order", {
    "Origin": TOOL_ORIGIN,
    "Content-Type": "application/json",
}, payload)
report("E. 业务请求 :8080/api/order  (无令牌，预期 401 但需带 CORS 头)",
       s, h, d, expect_body=True)

# F. 先登录拿令牌，再带着令牌 + Origin 请求业务接口 —— 检查是否出现「两个 ACAO」
login_body = json.dumps({"username": "admin", "password": "admin123"}).encode()
s, h, d = req(8080, "POST", "/api/auth/login", {
    "Origin": TOOL_ORIGIN,
    "Content-Type": "application/json",
}, login_body)
report("F. 登录 POST :8080/api/auth/login", s, h, d, expect_body=True)
token = None
try:
    obj = json.loads(d.decode("utf-8"))
    # 常见结构：{code,data:{token}} / {data:{accessToken}} / {token}
    def dig(o, depth=0):
        if depth > 4 or not isinstance(o, dict):
            return None
        for k, v in o.items():
            if isinstance(v, str) and "token" in k.lower() and len(v) > 8:
                return v
            r = dig(v, depth + 1)
            if r:
                return r
        return None
    token = dig(obj)
    print("    解析到 token:", (token[:24] + "...") if token else None, "\n")
except Exception as ex:
    print("    解析登录响应失败:", ex, "\n")

if token:
    s, h, d = req(8080, "POST", "/api/order", {
        "Origin": TOOL_ORIGIN,
        "Content-Type": "application/json",
        "Authorization": "Bearer " + token,
    }, payload)
    report("G. 带令牌的实际 POST :8080/api/order  (检查重复 ACAO)",
           s, h, d, expect_body=True)
    acao = [v for k, v in h if k.lower() == "access-control-allow-origin"]
    print(">>> 重复头结论:", "❌ 出现 %d 个 ACAO（浏览器会拒绝）" % len(acao)
          if len(acao) > 1 else "✅ ACAO 只有 1 个，无重复")
else:
    print(">>> 未拿到令牌，跳过测试 G")
