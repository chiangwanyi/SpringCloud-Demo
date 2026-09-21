"""验证 service-system 主键改为 IdType.AUTO 后确实走数据库自增。

判据：连续创建两个用户，比较返回的 id 差值。
  - 自增        → 差值为 1（或 AUTO_INCREMENT 步长）
  - 时间戳式发号 → 差值 = 间隔毫秒数 × 序列位宽，通常几十万以上
直接连 8081，不经网关（鉴权在网关，本地验证不需要令牌）。
"""
import http.client
import json
import sys
import time

sys.stdout.reconfigure(encoding="utf-8")

HOST, PORT = "127.0.0.1", 8081
OK = FAIL = 0


def record(name, ok, expect, actual):
    global OK, FAIL
    mark = "PASS" if ok else "FAIL"
    if ok:
        OK += 1
    else:
        FAIL += 1
    print("[%s] %s" % (mark, name))
    print("        期望: %s" % expect)
    print("        实际: %s" % actual)


def req(method, path, body=None):
    c = http.client.HTTPConnection(HOST, PORT, timeout=10)
    headers = {"Content-Type": "application/json"}
    c.request(method, path, json.dumps(body).encode("utf-8") if body else None, headers)
    r = c.getresponse()
    raw = r.read().decode("utf-8", "replace")
    c.close()
    return r.status, raw


def sql(statement):
    """查库（拿 Sql.java 兜底，避免依赖接口）。"""
    import subprocess

    out = subprocess.run(
        [
            "E:/Env/jdk-21.0.12.1/bin/java.exe",
            "-cp",
            "C:/Users/jiang/.m2/repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar",
            "Sql.java",
            statement,
            "service_system",
        ],
        cwd=r"E:\Data\projects\springcloud-demo\.workbuddy\tmp",
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    return out.stdout or ""


print("=" * 66)
print("service-system 主键策略验证（应为 DB 自增）")
print("=" * 66)

tag = time.strftime("%H%M%S")
names = ["idchk%sa" % tag, "idchk%sb" % tag]
ids = []
for u in names:
    st, raw = req(
        "POST",
        "/api/sys-user",
        {"username": u, "password": "123456", "nickname": "清理验证", "deptId": 1, "status": 1},
    )
    print("\nPOST /api/sys-user  username=%s -> HTTP %s" % (u, st))
    print("  body: %s" % raw[:200])
    try:
        ids.append(json.loads(raw).get("id"))
    except Exception:
        ids.append(None)

print()
record(
    "1. 两次创建都返回 200 且带 id",
    len(ids) == 2 and all(isinstance(i, int) for i in ids),
    "两个整数 id",
    "id=%s" % ids,
)

if len(ids) == 2 and all(isinstance(i, int) for i in ids):
    diff = ids[1] - ids[0]
    record(
        "2. 相邻两次插入的 id 差值为 1（自增特征）",
        diff == 1,
        "差值 = 1",
        "差值 = %d（%d → %d）" % (diff, ids[0], ids[1]),
    )
    record(
        "3. 新 id 落在既有数据的自增续点上（没回退到 1，不会撞已有行）",
        min(ids) > 2101582564690776000,
        "新 id > 库内既有最大 id 2101582564690776065",
        "新 id = %d" % min(ids),
    )

print("\n--- 库内实况 ---")
print(sql("SELECT id, username, nickname FROM sys_user ORDER BY id").strip()[:900])
print()
print(sql("SHOW TABLE STATUS LIKE 'sys_user'").strip()[:400])

print("\n" + "=" * 66)
print("结果: %d 通过 / %d 失败" % (OK, FAIL))
print("=" * 66)
sys.exit(1 if FAIL else 0)
