#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""脱离进程树启动 java 的两种候选方案，各自启动后**立刻退出**，由外部单独检查存活。

    python detach_probe.py breakaway   # CREATE_BREAKAWAY_FROM_JOB
    python detach_probe.py wmi         # Win32_Process.Create（父进程是 WmiPrvSE）
    python detach_probe.py check 8084  # 单独检查端口
"""
import os
import subprocess
import sys
import time

sys.stdout.reconfigure(encoding="utf-8")

ROOT = r"E:\Data\projects\springcloud-demo"
JAVA = r"E:\Env\jdk-21.0.12.1\bin\java.exe"
JAR = os.path.join(ROOT, "service-id-gen", "target", "service-id-gen-0.0.1-SNAPSHOT.jar")


def listening(port):
    out = subprocess.run("netstat -ano -p tcp", shell=True, capture_output=True, text=True).stdout or ""
    for line in out.splitlines():
        p = line.split()
        if len(p) >= 4 and p[0].upper() == "TCP" and p[3].upper() == "LISTENING" and p[1].endswith(":%d" % port):
            return p[4]
    return None


def mode_breakaway(port):
    log = os.path.join(ROOT, ".workbuddy", "tmp", "log", "breakaway.log")
    fh = open(log, "wb")
    CREATE_BREAKAWAY_FROM_JOB = 0x01000000
    DETACHED_PROCESS = 0x00000008
    flags = DETACHED_PROCESS | CREATE_BREAKAWAY_FROM_JOB
    try:
        p = subprocess.Popen([JAVA, "-jar", JAR, "--server.port=%d" % port],
                             stdout=fh, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                             cwd=ROOT, creationflags=flags, close_fds=True)
        print("breakaway PID=%d" % p.pid)
    except OSError as e:
        print("breakaway 失败: %r" % (e,))


def mode_wmi(port):
    log = os.path.join(ROOT, ".workbuddy", "tmp", "log", "wmi.log")
    inner = '"%s" -jar "%s" --server.port=%d' % (JAVA, JAR, port)
    # cmd /c 包一层，把 stdout/stderr 重定向进日志（WMI 创建时不接管标准句柄）
    cmdline = 'cmd.exe /c ""%s" -jar "%s" --server.port=%d >> "%s" 2>&1"' % (JAVA, JAR, port, log)
    ps = ("$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create "
          "-Arguments @{CommandLine=%s}; "
          "Write-Output ('RETURNVALUE=' + $r.ReturnValue + ' PID=' + $r.ProcessId)"
          % ps_quote(cmdline))
    out = subprocess.run(["powershell", "-NoProfile", "-NonInteractive", "-Command", ps],
                         capture_output=True, text=True, encoding="utf-8", errors="replace")
    print("wmi stdout: %s" % (out.stdout or "").strip())
    print("wmi stderr: %s" % (out.stderr or "").strip()[:400])


def ps_quote(s):
    return "'" + s.replace("'", "''") + "'"


def mode_check(port):
    pid = listening(port)
    print("port %d listening pid=%s" % (port, pid))
    out = subprocess.run('tasklist /FI "IMAGENAME eq java.exe" /FO CSV /NH',
                         shell=True, capture_output=True, text=True).stdout or ""
    for l in out.splitlines():
        if l.strip():
            print("  " + l)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    cmd = sys.argv[1]
    if cmd == "breakaway":
        mode_breakaway(int(sys.argv[2]) if len(sys.argv) > 2 else 8084)
    elif cmd == "wmi":
        mode_wmi(int(sys.argv[2]) if len(sys.argv) > 2 else 8085)
    elif cmd == "check":
        mode_check(int(sys.argv[2]) if len(sys.argv) > 2 else 8084)
    return 0


if __name__ == "__main__":
    sys.exit(main())
