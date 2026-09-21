#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""本机验证环境巡检：java 进程 + 端口占用 + 关键构建产物。只读。"""
import os
import subprocess
import sys

PORTS = [8080, 8081, 8082, 8083, 8084, 8085]
ROOT = r"E:\Data\projects\springcloud-demo"

sys.stdout.reconfigure(encoding="utf-8")


def sh(cmd):
    p = subprocess.run(cmd, shell=True, capture_output=True, text=True, encoding="utf-8", errors="replace")
    return p.stdout or ""


print("=" * 70)
print("[java 进程]")
out = sh('tasklist /FI "IMAGENAME eq java.exe" /FO CSV /NH')
lines = [l for l in out.splitlines() if l.strip()]
if lines:
    for l in lines:
        print("  " + l)
else:
    print("  (无)")

print("\n[端口占用]")
out = sh("netstat -ano -p tcp")
found = False
for line in out.splitlines():
    parts = line.split()
    if len(parts) >= 4 and parts[0].upper() == "TCP" and parts[3].upper() == "LISTENING":
        addr = parts[1]
        port = int(addr.rsplit(":", 1)[-1]) if ":" in addr else -1
        if port in PORTS:
            print("  %-24s pid=%s" % (addr, parts[4]))
            found = True
if not found:
    print("  (8080-8085 均无监听)")

print("\n[构建产物]")
for mod in ("service-id-gen", "service-order", "service-gateway", "service-auth", "service-system"):
    tgt = os.path.join(ROOT, mod, "target")
    if not os.path.isdir(tgt):
        print("  %-16s (无 target)" % mod)
        continue
    names = []
    for f in sorted(os.listdir(tgt), reverse=True):
        p = os.path.join(tgt, f)
        if os.path.isfile(p):
            names.append("%s(%dKB)" % (f, os.path.getsize(p) // 1024))
    print("  %-16s %s" % (mod, ", ".join(names[:4]) if names else "(空)"))
print("=" * 70)
