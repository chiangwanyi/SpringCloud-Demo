#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""实证：DETACHED_PROCESS 启动 java 后，进程是否存活、stdout 是否落盘。"""
import os
import subprocess
import sys
import time

sys.stdout.reconfigure(encoding="utf-8")

ROOT = r"E:\Data\projects\springcloud-demo"
JAVA = r"E:\Env\jdk-21.0.12.1\bin\java.exe"
JAR = os.path.join(ROOT, "service-id-gen", "target", "service-id-gen-0.0.1-SNAPSHOT.jar")
LOG = os.path.join(ROOT, ".workbuddy", "tmp", "log", "probe8084.log")
PORT = 8084

DETACHED_PROCESS = 0x00000008
CREATE_NEW_PROCESS_GROUP = 0x00000200
CREATE_NO_WINDOW = 0x08000000


def listening(port):
    out = subprocess.run("netstat -ano -p tcp", shell=True, capture_output=True, text=True).stdout or ""
    for line in out.splitlines():
        p = line.split()
        if len(p) >= 4 and p[0].upper() == "TCP" and p[3].upper() == "LISTENING" and p[1].endswith(":%d" % port):
            return p[4]
    return None


def run_case(name, flags, with_stdout=True):
    print("\n=== %s (flags=0x%X, stdout=%s) ===" % (name, flags, with_stdout))
    if listening(PORT):
        print("  端口已被占用，先跳过")
        return
    fh = open(LOG, "wb")
    kwargs = dict(stdin=subprocess.DEVNULL, cwd=ROOT, creationflags=flags, close_fds=True)
    if with_stdout:
        kwargs["stdout"] = fh
        kwargs["stderr"] = subprocess.STDOUT
    try:
        p = subprocess.Popen([JAVA, "-jar", JAR, "--server.port=%d" % PORT], **kwargs)
    except Exception as e:  # noqa: BLE001
        print("  Popen 失败: %r" % (e,))
        return
    print("  启动 pid=%d" % p.pid)
    for i in range(24):
        time.sleep(1)
        rc = p.poll()
        lis = listening(PORT)
        if i % 4 == 3 or lis:
            print("  +%2ds poll=%s listen=%s logsize=%d" % (i + 1, rc, lis, os.path.getsize(LOG)))
        if lis:
            print("  ✅ 监听成功 pid=%s" % lis)
            subprocess.run("taskkill /PID %s /T /F" % lis, shell=True, capture_output=True)
            p.wait(timeout=10)
            break
        if rc is not None:
            print("  ❌ 进程已退出 rc=%s" % rc)
            break
    fh.close()
    raw = open(LOG, "rb").read()
    print("  日志前 400 字节: %r" % raw[:400])


cases = sys.argv[1] or "d,n,c,plain"
for c in cases.split(","):
    if c == "d":
        run_case("DETACHED_PROCESS|NEW_GROUP", DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP)
    elif c == "n":
        run_case("CREATE_NO_WINDOW|NEW_GROUP", CREATE_NO_WINDOW | CREATE_NEW_PROCESS_GROUP)
    elif c == "c":
        run_case("CREATE_NEW_CONSOLE", 0x00000010)
    elif c == "plain":
        run_case("无 creationflags", 0)
