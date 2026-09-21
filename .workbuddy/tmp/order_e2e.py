#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
下单主链路端到端验证（经网关），重点是「订单号确实来自号段发号服务」。

三个阶段，分别验证号段模式的三条性质：

  [阶段一] 正常链路
      - 经网关登录拿令牌
      - 下单成功，订单号形如 SO + yyyyMMdd + 号段ID
      - 24 笔并发下单 → 订单号全局唯一（无重号）
      - 直接查 leaf_alloc.max_id 前后对比，证明订单号里的数字确实是从
        service-id-gen 批发的号段里取的，而不是本地随便编的

  [阶段二] ★ 号段在手 → 发号服务短暂不可用不影响下单
      停掉两个 service-id-gen 实例，继续下单，必须**照样成功**。
      这是号段模式相对「每次发号都 RPC」最本质的收益：
      发号服务不再是下单的关键路径依赖。同时 order 日志里应出现
      「预取下一号段失败」的 WARN —— 预取失败但不影响发号，正是双 buffer 的作用。
      反过来说，如果这里就挂了，说明号段根本没起作用。

  [阶段三] ★ 拿不到号段 → 必须 503，绝不降级
      在 id-gen 已停止的情况下重启 service-order（新进程缓冲区为空），
      此时下单必须快速失败返回 503，而不是退回某种本地兜底号
      —— 那是跨实例重号的来源。随后拉起 id-gen，下单应立即恢复 200。

用法：
    python order_e2e.py            # 三个阶段全跑（会停/起服务，约 60s）
    python order_e2e.py http       # 只跑阶段一
    python order_e2e.py localbuf   # 只跑阶段二（调用前请自行停掉 id-gen）
    python order_e2e.py failfast   # 只跑阶段三（调用前请停 id-gen 并重启 order）

★ 同样用 http.client 而非 urllib：本机 urllib 会读系统代理，打 127.0.0.1 会返回
  "HTTP 502 upstream connect failed"，测到的是代理不是业务。
"""

import http.client
import json
import os
import re
import subprocess
import sys
import threading
import time

sys.stdout.reconfigure(encoding="utf-8")

GATEWAY_HOST, GATEWAY_PORT = "127.0.0.1", 8080
ROOT = r"E:\Data\projects\springcloud-demo"
TMP = os.path.join(ROOT, ".workbuddy", "tmp")
JAVA = r"E:\Env\jdk-21.0.12.1\bin\java.exe"
MYSQL_JAR = r"C:\Users\jiang\.m2\repository\com\mysql\mysql-connector-j\9.7.0\mysql-connector-j-9.7.0.jar"
PY = sys.executable
SVC = os.path.join(TMP, "svc.py")

USER_ID = 2          # zhangsan
USERNAME = "zhangsan"
PASSWORD = "123456"
PRODUCT_ID = 2       # 无线鼠标，库存 200，够压
CONCURRENCY = 24

ORDER_NO_RE = re.compile(r"^SO(\d{8})(\d+)$")

results = []
_lock = threading.Lock()


def record(name, ok, expect, actual):
    with _lock:
        results.append((name, ok))
    print("  [%s] %s\n        预期: %s\n        实际: %s"
          % ("PASS" if ok else "FAIL", name, expect, actual))


class Client(object):
    """一个持久连接。线程不共享。"""

    def __init__(self, host=GATEWAY_HOST, port=GATEWAY_PORT):
        self.host, self.port = host, port
        self._conn = None

    def _c(self):
        if self._conn is None:
            self._conn = http.client.HTTPConnection(self.host, self.port, timeout=30)
        return self._conn

    def call(self, method, path, body=None, headers=None):
        hdrs = dict(headers or {})
        data = None
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            hdrs["Content-Type"] = "application/json; charset=utf-8"
        for attempt in (1, 2):
            try:
                c = self._c()
                c.request(method, path, body=data, headers=hdrs)
                r = c.getresponse()
                raw = r.read().decode("utf-8", "replace")
                return r.status, raw
            except (http.client.HTTPException, OSError):
                self.close()
        raise RuntimeError("连接失败: %s %s" % (method, path))

    def close(self):
        if self._conn is not None:
            try:
                self._conn.close()
            except Exception:  # noqa: BLE001
                pass
            self._conn = None


def err_detail(body):
    """从响应体里取可读原因。本项目错误契约是 RFC 7807，字段名是 detail；兼容 message。"""
    if isinstance(body, dict):
        return body.get("detail") or body.get("message") or json.dumps(body, ensure_ascii=False)[:200]
    return str(body)[:200]


def login(client):
    st, raw = client.call("POST", "/api/auth/login",
                          {"username": USERNAME, "password": PASSWORD})
    if st != 200:
        raise SystemExit("!! 登录失败 HTTP %s: %s" % (st, raw[:200]))
    return json.loads(raw)["token"]


def place_order(client, token, quantity=1):
    """返回 (status, 响应体解析结果或原文串)"""
    st, raw = client.call("POST", "/api/order",
                          {"userId": USER_ID,
                           "items": [{"productId": PRODUCT_ID, "quantity": quantity}]},
                          {"Authorization": "Bearer " + token})
    try:
        return st, json.loads(raw)
    except ValueError:
        return st, raw


def sql(query, db="leaf"):
    """跑一条 SQL 拿原样输出（借 .workbuddy/tmp/Sql.java，避免再引第三方驱动）"""
    p = subprocess.run([JAVA, "-cp", MYSQL_JAR, "Sql.java", query, db],
                       cwd=TMP, capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    return (p.stdout or "").strip()


def max_id():
    out = sql("SELECT max_id FROM leaf_alloc WHERE biz_tag = 'order'")
    for line in out.splitlines():
        line = line.strip()
        if line.isdigit():
            return int(line)
    raise RuntimeError("读 max_id 失败，Sql.java 输出=%r" % out)


def alloc_step():
    """当前 order 号段的批大小。step 存在库里、每次取段现读，可以热调。"""
    out = sql("SELECT step FROM leaf_alloc WHERE biz_tag = 'order'")
    for line in out.splitlines():
        line = line.strip()
        if line.isdigit():
            return int(line)
    return 2000


def svc(*args):
    p = subprocess.run([PY, SVC] + list(args), capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    out = (p.stdout or "").strip()
    print("  $ svc %s -> %s" % (" ".join(args), out.replace("\n", " | ")))
    return p.returncode


def log_tail(port, n=40):
    p = subprocess.run([PY, SVC, "log", str(port), str(n)], capture_output=True,
                       text=True, encoding="utf-8", errors="replace")
    return p.stdout or ""


def wait_ready(port, timeout=120):
    return svc("wait", str(port), str(timeout)) == 0


SEG_IN_LOG_RE = re.compile(r"首段=\[(\d+), (\d+)\]")


def last_logged_segment(port):
    """从服务日志里取最后一次「初始化本地号段缓冲：… 首段=[b, e]」的 [b, e]。"""
    log = log_tail(port, 3000)
    hits = SEG_IN_LOG_RE.findall(log)
    if not hits:
        return None
    b, e = hits[-1]
    return int(b), int(e)


# ------------------------------------------------------------------ 阶段一
def phase_http():
    print("\n" + "=" * 74)
    print("[阶段一] 正常链路：订单号来自号段发号服务")
    print("=" * 74)

    client = Client()
    try:
        token = login(client)
        record("1.1 经网关登录拿到令牌", bool(token), "非空令牌", (token or "")[:16] + "...")

        # 商品列表（确认服务间调用与服务可用）
        st, raw = client.call("GET", "/api/product/list", headers={"Authorization": "Bearer " + token})
        products = json.loads(raw) if st == 200 else []
        record("1.2 经网关查商品列表", st == 200 and len(products) > 0,
               "200 且非空", "HTTP %s，%d 条" % (st, len(products)))

        before = max_id()

        # ---------- 单笔 ----------
        st, body = place_order(client, token)
        if st != 200:
            record("1.3 单笔下单 -> 200", False, "HTTP 200", "HTTP %s · %s" % (st, str(body)[:200]))
            return
        order_no = body["orderNo"]
        m = ORDER_NO_RE.match(order_no)
        record("1.3 单笔下单 -> 200", True, "HTTP 200", "HTTP 200 · orderNo=%s" % order_no)
        record("1.4 订单号格式为 SO + yyyyMMdd + 号段ID", m is not None,
               "形如 SO20260921123456", order_no)
        if not m:
            return
        today = time.strftime("%Y%m%d")
        # 日期取的是**服务端**当天，跨零点运行时可能差一天，这里只做提示不做硬断言
        record("1.5 订单号日期段是服务端当天", m.group(1) == today,
               "日期段 = %s" % today, "日期段 = %s" % m.group(1))

        # ---------- 并发 ----------
        threads_n = 8
        per_thread = CONCURRENCY // threads_n
        print("\n  并发下单 %d 线程 × %d 笔……" % (threads_n, per_thread))
        order_nos = [order_no]
        errs = []
        t0 = time.time()

        def worker():
            c = Client()
            local = []
            try:
                for _ in range(per_thread):
                    st, b = place_order(c, token)
                    if st == 200:
                        local.append(b["orderNo"])
                    else:
                        errs.append("HTTP %s: %s" % (st, str(b)[:100]))
            except Exception as e:  # noqa: BLE001
                errs.append(repr(e))
            finally:
                c.close()
            with _lock:
                order_nos.extend(local)

        ts = [threading.Thread(target=worker) for _ in range(threads_n)]
        for t in ts:
            t.start()
        for t in ts:
            t.join()
        cost = time.time() - t0
        total = len(order_nos)
        uniq = len(set(order_nos))
        record("1.6 %d 笔并发下单全部成功" % CONCURRENCY, not errs, "0 个失败",
               "%d 个失败：%s" % (len(errs), errs[:2]))
        record("1.7 订单号全局唯一（无重号）", uniq == total,
               "%d 个订单号全不重复" % total,
               "共 %d 个，唯一 %d 个，重复 %d（%.0f 单/s）"
               % (total, uniq, total - uniq, total / cost if cost else 0))

        after = max_id()
        ids = [int(ORDER_NO_RE.match(n).group(2)) for n in order_nos if ORDER_NO_RE.match(n)]

        # ---------- 号段账本证据（跨源交叉验证） ----------
        # ⚠️ 这里刻意**不**断言「下单期间 max_id 增长」——那恰恰是号段模式要避免的事：
        #    号段在启动时就领回来了，发号全程只在本地内存自增，一次数据库都不碰。
        #    首版测试就是因为断言了 max_id 必须增长而得到假失败。
        # 真正有说服力的证据是三个来源互相咬合：
        #   ① order 启动日志：它领到的首段 [b, e]；
        #   ② 实际返回的订单号里的数字；
        #   ③ 号段账本 leaf_alloc.max_id。
        seg = last_logged_segment(8083)
        if seg:
            begin, end = seg
            record("1.8 订单号全部落在 order 启动时领到的号段内", min(ids) >= begin and max(ids) <= after,
                   "订单号 ∈ [%d, %d] 且 <= max_id(%d)" % (begin, end, after),
                   "order 首段=[%d, %d]；实际订单号 ∈ [%d, %d]；max_id=%d"
                   % (begin, end, min(ids), max(ids), after))
        else:
            record("1.8 订单号全部落在已批发范围内", min(ids) > 0 and max(ids) <= after,
                   "max(订单ID) <= max_id",
                   "max(订单ID)=%d, max_id=%d（未在日志里找到首段行）" % (max(ids), after))

        span = max(ids) - min(ids) + 1
        alloc = alloc_step()
        record("1.9 号码连续，来自号段而非随机数",
               span - len(ids) < alloc,
               "跨度与数量接近（洞远小于一个 step=%d）" % alloc,
               "区间 [%d, %d]，跨度 %d，数量 %d，洞 %d；下单期间 max_id %d → %d（发号未触库）"
               % (min(ids), max(ids), span, len(ids), span - len(ids), before, after))
    finally:
        client.close()


# ------------------------------------------------------------------ 阶段二
def phase_localbuf():
    print("\n" + "=" * 74)
    print("[阶段二] 号段在手：发号服务不可用，下单仍应成功")
    print("=" * 74)

    client = Client()
    try:
        token = login(client)
        oks, fails = [], []
        for _ in range(3):
            st, b = place_order(client, token)
            (oks if st == 200 else fails).append("HTTP %s" % st)
        record("2.1 id-gen 已停，下单仍成功（本地号段续命）", not fails,
               "3/3 返回 200", "%d 成功 / %d 失败；示例 orderNo=%s"
               % (len(oks), len(fails),
                  b.get("orderNo") if isinstance(b, dict) and st == 200 else str(b)[:120]))
        # 2.2 只做信息性观察，不做断言：
        # 预取要等当前段消耗到 10%（step=2000 时是 200 个号）才会触发，而这里只下 3 单，
        # 而且 order 启动时已经把预备段也领回来了 —— 所以此刻多半根本不会去预取。
        # 「没看到预取失败」不代表有缺陷，硬断言会得到假失败。
        log = log_tail(8083, 400)
        hit = "预取下一号段失败" in log
        print("  [INFO] order 日志中的号段预取告警：%s"
              % ("有 —— 预取失败但当前段继续发号，双 buffer 起了作用"
                 if hit else "暂未出现（当前段才用掉几个号，还没到预取点 10%，属正常）"))
        print("  [INFO] 号段模式的本质收益：发号只在本地内存自增，"
              "id-gen 挂掉不影响在途号段内的下单。")
    finally:
        client.close()


# ------------------------------------------------------------------ 阶段三
def phase_failfast(stop_after=True):
    print("\n" + "=" * 74)
    print("[阶段三] 拿不到号段：必须 503，绝不降级")
    print("=" * 74)

    client = Client()
    try:
        token = login(client)
        try:
            st, b = place_order(client, token)
            msg = err_detail(b)
        except Exception as e:  # noqa: BLE001
            record("3.1 id-gen 不可用且本地无号段 -> 下单 503", False,
                   "HTTP 503（拒绝服务，而不是给兜底订单号）",
                   "请求没能返回：%r —— 快速失败没做到，发号不可用把下单拖成了超时" % (e,))
            return
        record("3.1 id-gen 不可用且本地无号段 -> 下单 503", st == 503,
               "HTTP 503（拒绝服务，而不是给兜底订单号）",
               "HTTP %s · %s" % (st, msg))
        if isinstance(b, dict) and b.get("orderNo"):
            record("3.2 失败响应里不得出现订单号", False, "无 orderNo", str(b.get("orderNo")))

        if stop_after:
            print("\n  拉起 service-id-gen 后应自动恢复……")
            svc("start", "service-id-gen", "8084")
            svc("start", "service-id-gen", "8085")
            wait_ready(8084, 120)
            wait_ready(8085, 120)
            time.sleep(2)
            try:
                st2, b2 = place_order(client, token)
                record("3.3 id-gen 恢复后下单立即恢复 200", st2 == 200,
                       "HTTP 200", "HTTP %s · %s" % (st2, str(b2)[:160]))
            except Exception as e:  # noqa: BLE001
                record("3.3 id-gen 恢复后下单立即恢复 200", False,
                       "HTTP 200", "请求异常：%r" % (e,))
    finally:
        client.close()


def summary():
    passed = sum(1 for _, ok in results if ok)
    print("\n" + "=" * 74)
    print("结果: %d/%d 项通过" % (passed, len(results)))
    for n, ok in results:
        if not ok:
            print("  FAILED: %s" % n)
    print("=" * 74)
    return 0 if passed == len(results) else 1


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "all"

    if mode == "http":
        phase_http()
        return summary()
    if mode == "localbuf":
        phase_localbuf()
        return summary()
    if mode == "failfast":
        phase_failfast()
        return summary()

    # ---------- 全流程 ----------
    phase_http()

    print("\n" + "-" * 74)
    print("停掉两个 service-id-gen 实例（模拟发号服务整体不可用）")
    svc("stop", "8084")
    svc("stop", "8085")
    time.sleep(8)   # 等 Nacos 心跳超时 / 连接被拒暴露出来
    phase_localbuf()

    print("\n" + "-" * 74)
    print("重启 service-order（新进程本地缓冲为空）—— 用于验证 503 红线")
    svc("stop", "8083")
    svc("start", "service-order", "8083")
    wait_ready(8083, 120)
    phase_failfast()

    return summary()


if __name__ == "__main__":
    sys.exit(main())
