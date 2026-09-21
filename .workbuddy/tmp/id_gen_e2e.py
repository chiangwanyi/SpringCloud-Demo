#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
service-id-gen（Leaf-segment 号段模式）端到端验证。

只依赖标准库。★ 刻意用 http.client 而不是 urllib.request：
  1) urllib 会读系统 http_proxy 环境变量，本机环境下打 127.0.0.1 会被代理截住，
     返回 "HTTP 502 upstream connect failed"，测出来的是代理而不是发号服务；
  2) http.client 的连接是可以复用的，几千次请求只占几条 TCP 连接，
     不会因为每条请求新开连接而把本机临时端口打满（WSAEADDRINUSE 10048）。
（本项目的 .workbuddy/tmp/e2e_auth.py 当初也是同样的选择。）

用法：
    python id_gen_e2e.py                          # 单实例 http://127.0.0.1:8084
    python id_gen_e2e.py --urls http://127.0.0.1:8084,http://127.0.0.1:8085
"""

import argparse
import http.client
import json
import sys
import threading
import time
import urllib.parse

BIZ = "order"
results = []
_lock = threading.Lock()
retry_counter = [0]


def record(name, ok, expect, actual):
    with _lock:
        results.append((name, ok, expect, actual))
    print("  [%s] %s\n        预期: %s\n        实际: %s" % ("PASS" if ok else "FAIL", name, expect, actual))


class Client(object):
    """一个实例的持久连接客户端。线程不共享 —— 每个线程自己 new 一个。"""

    def __init__(self, base):
        self.base = base.rstrip("/")
        hostport = self.base.split("://", 1)[-1].split("/", 1)[0]
        if ":" in hostport:
            host, port = hostport.rsplit(":", 1)
            self.host, self.port = host, int(port)
        else:
            self.host, self.port = hostport, 80
        self._conn = None

    def _connection(self):
        if self._conn is None:
            self._conn = http.client.HTTPConnection(self.host, self.port, timeout=30)
        return self._conn

    def get(self, path):
        """返回 (status, body)。连接层异常重连一次，重试次数计入统计。"""
        last = None
        for attempt in (1, 2):
            try:
                conn = self._connection()
                conn.request("GET", path)
                resp = conn.getresponse()
                body = resp.read().decode("utf-8", "replace")
                return resp.status, body
            except (http.client.HTTPException, OSError) as e:
                last = e
                self.close()
            if attempt == 1:
                with _lock:
                    retry_counter[0] += 1
        raise RuntimeError("连接失败: %r" % (last,))

    def close(self):
        if self._conn is not None:
            try:
                self._conn.close()
            except Exception:  # noqa: BLE001
                pass
            self._conn = None


def path_next(biz=None):
    return "/api/id/next?%s" % urllib.parse.urlencode({"bizTag": biz or BIZ})


def path_segment(biz=None):
    return "/api/id/segment?%s" % urllib.parse.urlencode({"bizTag": biz or BIZ})


def next_id(client, biz=None):
    status, body = client.get(path_next(biz))
    if status != 200:
        raise RuntimeError("HTTP %s: %s" % (status, body[:200]))
    return int(body.strip())


def fetch_segment(client, biz=None):
    status, body = client.get(path_segment(biz))
    if status != 200:
        raise RuntimeError("HTTP %s: %s" % (status, body[:200]))
    return json.loads(body)


# ---------------------------------------------------------------- A / B
def test_basic(urls):
    print("\n[A] 服务可用性")
    client = Client(urls[0])
    ids = [next_id(client) for _ in range(5)]
    record("A1 /api/id/next 连续 5 次都返回数字", all(isinstance(i, int) for i in ids),
           "5 个整数", str(ids))
    record("A2 返回值严格递增", all(ids[i] < ids[i + 1] for i in range(len(ids) - 1)),
           "递增", str(ids))
    client.close()


def test_status(urls):
    print("\n[B] 状态接口（观察双 buffer 的关键窗口）")
    client = Client(urls[0])
    status, body = client.get("/api/id/status")
    items = json.loads(body) if status == 200 else []
    st = next((i for i in items if i.get("bizTag") == BIZ), None)
    record("B1 /api/id/status 含 bizTag=%s" % BIZ, st is not None, "有该条目",
           json.dumps(st, ensure_ascii=False) if st else "无（HTTP %s）" % status)
    if st:
        record("B2 预备段已就绪（双 buffer 预取生效）", st.get("nextReady") is True,
               "nextReady = true",
               "nextReady = %s，prefetching = %s" % (st.get("nextReady"), st.get("prefetching")))
    client.close()


# ---------------------------------------------------------------- C
def test_single_instance(urls, threads, per_thread):
    print("\n[C] 单实例并发唯一性（%d 线程 × %d 次）" % (threads, per_thread))
    ids = []
    errors = []

    def worker():
        client = Client(urls[0])
        local = []
        try:
            for _ in range(per_thread):
                local.append(next_id(client))
        except Exception as e:  # noqa: BLE001
            errors.append(repr(e))
        finally:
            client.close()
        with _lock:
            ids.extend(local)

    t0 = time.time()
    ts = [threading.Thread(target=worker) for _ in range(threads)]
    for t in ts:
        t.start()
    for t in ts:
        t.join()
    cost = time.time() - t0

    total = threads * per_thread
    uniq = len(set(ids))
    record("C1 无重号", uniq == len(ids) and uniq > 0,
           "%d 个全不重复" % total,
           "%d 个，唯一 %d 个，重复 %d" % (len(ids), uniq, len(ids) - uniq))
    record("C2 无异常", not errors, "0 个异常",
           "%d 个：%s" % (len(errors), errors[:2]))
    if ids:
        span = max(ids) - min(ids) + 1
        holes = span - len(ids)
        print("        QPS ≈ %.0f/s；数值区间 [%d, %d]，跨度 %d，数量 %d，洞 %d"
              % (len(ids) / cost if cost else 0, min(ids), max(ids), span, len(ids), holes))
        if holes:
            print("        洞的成因（都不是缺陷，ID 不要求连续）：\n"
                  "          ① 同一 bizTag 的号段被判给了另一个实例 —— 本实例取新段时自然跳过那一段；\n"
                  "          ② 每次换段会烧掉 1 个 end+1（越界那一次 nextId 的返回值）。\n"
                  "        所以：单实例空跑时洞 ≈ 换段次数；多实例混跑时洞 ≈ 另一个实例占着的号段总量。")
    return ids


# ---------------------------------------------------------------- D
def test_multi_instance(urls, threads, per_thread):
    print("\n[D] ★ 多实例交叉发号唯一性（%d 个实例，每实例 %d 线程 × %d 次）"
          % (len(urls), threads, per_thread))
    ids = []
    errors = []
    per_url = {}

    def worker(base):
        client = Client(base)
        local = []
        try:
            for _ in range(per_thread):
                local.append(next_id(client))
        except Exception as e:  # noqa: BLE001
            errors.append("%s -> %r" % (base, e))
        finally:
            client.close()
        with _lock:
            ids.extend(local)
            per_url.setdefault(base, []).extend(local)

    ts = [threading.Thread(target=worker, args=(u,)) for u in urls for _ in range(threads)]
    for t in ts:
        t.start()
    for t in ts:
        t.join()

    total = len(urls) * threads * per_thread
    uniq = len(set(ids))
    record("D1 跨实例无重号（DB 行锁仲裁生效）", uniq == len(ids) and uniq > 0,
           "%d 个全不重复" % total,
           "%d 个，唯一 %d 个，重复 %d" % (len(ids), uniq, len(ids) - uniq))
    record("D2 无异常", not errors, "0 个异常", "%d 个：%s" % (len(errors), errors[:2]))
    for u, vals in sorted(per_url.items()):
        if vals:
            print("        %s 发出 %d 个，数值区间 [%d, %d]" % (u, len(vals), min(vals), max(vals)))
    # ★ 这里**不能**拿两个实例 ID 的 min/max 去比"区间不相交"。
    #   号段是「按需领」的：A 可能在刚领的 [10001,12000] 里发，B 却在更早领的
    #   [8001,10000] 里继续发 → 两个实例的**数值区间天然会交织**。
    #   区间交织 ≠ 重号，拿它当断言会得到假失败（首版就踩了这个坑）。
    #   真正该断言的、也是行锁仲裁的直接证据，是「两个实例各自发出的 ID 集合互不相交」。
    if len(per_url) == 2:
        (ua, va), (ub, vb) = sorted(per_url.items())
        a, b = set(va), set(vb)
        inter = a & b
        record("D3 两实例各自发出的 ID 集合互不相交", not inter,
               "交集为空（区间可以交织，号不能重）",
               "交集 %d 个%s" % (len(inter), ("：" + str(sorted(inter)[:5])) if inter else ""))
    return ids


# ---------------------------------------------------------------- E
def test_segment_no_overlap(urls, rounds=8):
    print("\n[E] 领段接口区间不重叠（%d 个实例各领 %d 次）" % (len(urls), rounds))
    segs = []
    for base in urls:
        client = Client(base)
        try:
            for _ in range(rounds):
                d = fetch_segment(client)
                segs.append((d["begin"], d["end"], base))
        finally:
            client.close()
    segs.sort()
    overlap = [(segs[i - 1], segs[i]) for i in range(1, len(segs)) if segs[i][0] <= segs[i - 1][1]]
    record("E1 区间互不相交且严格递增", not overlap, "0 处重叠",
           "%d 处重叠：%s" % (len(overlap), overlap[:2]))
    if segs:
        covered = sum(e - b + 1 for b, e, _ in segs)
        print("        共领 %d 段，覆盖 %d 个号；最小 [%d,%d]，最大 [%d,%d]"
              % (len(segs), covered, segs[0][0], segs[0][1], segs[-1][0], segs[-1][1]))


# ---------------------------------------------------------------- F
def test_error_handling(urls):
    print("\n[F] 错误处理：未配置的 bizTag 必须快速失败")
    client = Client(urls[0])
    try:
        status, body = client.get(path_next("__not_configured__"))
        record("F1 /api/id/next 未配置 bizTag → 503（绝不返回空段或降级发号）", status == 503,
               "HTTP 503", "HTTP %s · %s" % (status, body[:160].replace("\n", " ")))
        status, body = client.get(path_segment("__not_configured__"))
        record("F2 /api/id/segment 未配置 bizTag → 503", status == 503, "HTTP 503",
               "HTTP %s · %s" % (status, body[:160].replace("\n", " ")))
    finally:
        client.close()


def main():
    # global 必须写在函数体最前面：只要函数里有任何一处用到 BIZ，
    # 在它之后再写 global BIZ 就会报 "name is used prior to global declaration"
    global BIZ

    ap = argparse.ArgumentParser()
    ap.add_argument("--urls", default="http://127.0.0.1:8084")
    ap.add_argument("--threads", type=int, default=16)
    ap.add_argument("--per-thread", type=int, default=2000)
    ap.add_argument("--biz", default=BIZ,
                    help="测试用的 bizTag。★ 别用业务在用的 tag，"
                         "因为测试会重置 max_id；建议在 leaf_alloc 里另建一行，例如 demo")
    args = ap.parse_args()

    BIZ = args.biz
    urls = [u.strip().rstrip("/") for u in args.urls.split(",") if u.strip()]
    print("=" * 78)
    print("service-id-gen 端到端验证")
    print("实例: %s" % ", ".join(urls))
    print("=" * 78)

    try:
        test_basic(urls)
        test_status(urls)
        test_single_instance(urls, args.threads, args.per_thread)
        if len(urls) > 1:
            test_multi_instance(urls, max(2, args.threads // 2), args.per_thread)
        test_segment_no_overlap(urls)
        test_error_handling(urls)
    except Exception as e:  # noqa: BLE001
        print("\n!! 验证中断: %r" % (e,))
        print("   请确认 service-id-gen 已启动，且 leaf_alloc 里有 bizTag=%s 的种子行" % BIZ)
        sys.exit(2)

    passed = sum(1 for _, ok, _, _ in results if ok)
    print("\n" + "=" * 78)
    print("结果: %d/%d 项通过；连接层重试 %d 次" % (passed, len(results), retry_counter[0]))
    for name, ok, _, _ in results:
        if not ok:
            print("  FAILED: %s" % name)
    print("=" * 78)
    sys.exit(0 if passed == len(results) else 1)


if __name__ == "__main__":
    main()
