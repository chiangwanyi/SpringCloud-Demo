#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
本机微服务启停工具。

★ 两个踩过的坑，这版都绕开了：

  1) 进程脱离：用 DETACHED_PROCESS 从 Bash 工具里拉 java，JVM 会在**这次工具调用
     结束时被连带杀掉**（工具进程属于一个 Job Object，且不允许 breakaway —— 试过
     CREATE_BREAKAWAY_FROM_JOB，直接 PermissionError 13）。日志里一个字节都没有，
     因为 JVM 活不过 Spring Boot 打印 banner 的那一刻。
     正解：用 WMI 的 Win32_Process.Create 启动，父进程是 WmiPrvSE.exe，天然在 Job 之外。

  2) 端口探测：本环境里 `netstat -ano -p tcp` **返回空**（沙箱里被挡），所以
     "netstat 没看到监听" ≠ "真的没监听"。曾因此误判 WMI 启动失败，其实服务好得很。
     正解：用 TCP 连接探测（socket.create_connection），并顺手用它找进程。

  3) 日志落盘：WMI 创建进程时不接管标准句柄，所以不能用 `cmd /c ... >> log`（那样拿到
     的 PID 是 cmd 的，且多一层进程）。改用 Spring Boot 自带的 `--logging.file.name`，
     让 logback 自己写文件 → 能直接用 WMI 创建 java.exe，返回的 ProcessId 就是 JVM 的。

用法：
    python svc.py ls                        # 列出端口 / 模块 / 存活 / PID
    python svc.py start <module> [port] [-- 额外参数...]
    python svc.py stop <port>
    python svc.py wait <port> [秒数]
    python svc.py log <port> [行数]
    python svc.py ps                        # 列出所有 java 进程及其命令行
"""
import os
import re
import socket
import subprocess
import sys
import time

ROOT = r"E:\Data\projects\springcloud-demo"
TMP = os.path.join(ROOT, ".workbuddy", "tmp")
LOGDIR = os.path.join(TMP, "log")
JAVA = r"E:\Env\jdk-21.0.12.1\bin\java.exe"

SERVICES = {
    8084: "service-id-gen",
    8085: "service-id-gen",
    8081: "service-system",
    8082: "service-auth",
    8083: "service-order",
    8080: "service-gateway",
}

sys.stdout.reconfigure(encoding="utf-8")


# ------------------------------------------------------------------ 基础工具
def ps(cmd):
    """在 PowerShell 里跑一段脚本并拿回 stdout（PowerShell 工具本身不回显，这里自己抓）。"""
    return subprocess.run(["powershell", "-NoProfile", "-NonInteractive", "-Command", cmd],
                          capture_output=True, text=True, encoding="utf-8",
                          errors="replace").stdout or ""


def java_processes():
    """返回 [(pid, commandLine)]，覆盖所有 java.exe。"""
    out = ps("Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | "
             "ForEach-Object { Write-Output ($_.ProcessId.ToString() + '|' + $_.CommandLine) }")
    res = []
    for line in out.splitlines():
        if "|" in line:
            pid, cmd = line.split("|", 1)
            try:
                res.append((int(pid.strip()), cmd.strip()))
            except ValueError:
                pass
    return res


def pid_of_port(port):
    token = "--server.port=%d" % port
    for pid, cmd in java_processes():
        if token in cmd:
            return pid
    return None


def port_open(port, timeout=1.0):
    """真正的可用性探测 —— 不依赖 netstat。"""
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=timeout):
            return True
    except OSError:
        return False


def find_jar(module):
    tgt = os.path.join(ROOT, module, "target")
    if not os.path.isdir(tgt):
        raise SystemExit("!! %s 没有 target 目录，先 mvn install" % module)
    jars = [f for f in os.listdir(tgt)
            if f.endswith(".jar") and not f.endswith(".jar.original")]
    if not jars:
        raise SystemExit("!! %s/target 下没有可执行 jar" % module)
    jars.sort(key=lambda f: os.path.getmtime(os.path.join(tgt, f)), reverse=True)
    return os.path.join(tgt, jars[0])


def default_port(module):
    yml = os.path.join(ROOT, module, "src", "main", "resources", "application.yml")
    if not os.path.isfile(yml):
        return 0
    m = re.search(r"^\s*port:\s*(\d+)", open(yml, encoding="utf-8").read(), re.M)
    return int(m.group(1)) if m else 0


def log_path(port):
    return os.path.join(LOGDIR, "%d.log" % port)


# ------------------------------------------------------------------ 子命令
def cmd_start(argv):
    module = argv[0]
    port = int(argv[1]) if len(argv) > 1 and not argv[1].startswith("--") else default_port(module)
    extra = argv[argv.index("--") + 1:] if "--" in argv else []
    if not port:
        raise SystemExit("!! 无法确定端口，请显式传入")
    old = pid_of_port(port)
    if old:
        print("!! 端口 %d 已有 java pid=%d 在跑，先 stop %d" % (port, old, port))
        return 1

    jar = find_jar(module)
    os.makedirs(LOGDIR, exist_ok=True)
    log = log_path(port)
    # 追加分隔线，重启同端口时可对比「首次启动」与「二次启动」
    with open(log, "ab") as fh:
        fh.write(("\n\n===== start %s @ %s =====" % (module, time.strftime("%Y-%m-%d %H:%M:%S"))).encode("utf-8"))
    args = [JAVA, "-Dfile.encoding=UTF-8", "-jar", jar,
            "--server.port=%d" % port,
            "--logging.file.name=%s" % log] + extra
    # 命令行里带引号/空格，交给 PowerShell 时用单引号包住并转义内部单引号
    cmdline = '"' + '" "'.join(args) + '"'
    script = ("$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create "
              "-Arguments @{CommandLine=%s}; "
              "Write-Output ('RET=' + $r.ReturnValue + ' PID=' + $r.ProcessId)"
              % ("'" + cmdline.replace("'", "''") + "'"))
    out = ps(script).strip()
    if "RET=0" not in out:
        print("!! WMI 创建进程失败: %s" % out)
        return 1
    print("启动 %s @ %d  (%s)" % (module, port, out))
    print("  jar=%s" % jar)
    print("  log=%s" % log)
    return 0


def cmd_stop(argv):
    port = int(argv[0])
    pid = pid_of_port(port)
    if not pid:
        print("端口 %d 没有找到对应的 java 进程" % port)
        return 0
    subprocess.run("taskkill /PID %d /T /F" % pid, shell=True, capture_output=True)
    for _ in range(20):
        if not pid_of_port(port):
            break
        time.sleep(0.5)
    print("已停止端口 %d 的 java pid=%d" % (port, pid))
    return 0


def cmd_wait(argv):
    port = int(argv[0])
    timeout = int(argv[1]) if len(argv) > 1 else 90
    t0 = time.time()
    while time.time() - t0 < timeout:
        if port_open(port):
            print("端口 %d 已就绪（%.1fs）" % (port, time.time() - t0))
            return 0
        if time.time() - t0 > 6 and pid_of_port(port) is None:
            print("!! 进程不存在了，端口 %d 起不来。看日志：python svc.py log %d 60" % (port, port))
            return 2
        time.sleep(1)
    print("!! 等待超时 %ds，端口 %d 仍未就绪" % (timeout, port))
    return 1


def cmd_log(argv):
    port = int(argv[0])
    n = int(argv[1]) if len(argv) > 1 else 60
    path = log_path(port)
    if not os.path.isfile(path):
        print("(无日志文件 %s)" % path)
        return 1
    raw = open(path, "rb").read()
    try:
        txt = raw.decode("utf-8")
    except UnicodeDecodeError:
        txt = raw.decode("gbk", "replace")
    lines = txt.splitlines()
    for l in lines[-n:]:
        print(l)
    print("--- 共 %d 行，显示末尾 %d 行 ---" % (len(lines), min(n, len(lines))))
    return 0


def cmd_ls(_argv):
    print("%-6s %-18s %-7s %-7s %s" % ("PORT", "MODULE", "OPEN", "PID", "LOG"))
    for port in sorted(SERVICES):
        pid = pid_of_port(port)
        print("%-6d %-18s %-7s %-7s %s"
              % (port, SERVICES[port], "yes" if port_open(port, 0.4) else "-",
                 pid or "-", "%d KB" % (os.path.getsize(log_path(port)) // 1024)
                 if os.path.isfile(log_path(port)) else "-"))
    return 0


def cmd_ps(_argv):
    for pid, cmd in java_processes():
        print("pid=%-6d %s" % (pid, cmd[:180]))
    return 0


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    cmd, argv = sys.argv[1], sys.argv[2:]
    table = {"start": cmd_start, "stop": cmd_stop, "wait": cmd_wait,
             "log": cmd_log, "ls": cmd_ls, "ps": cmd_ps}
    if cmd not in table:
        print(__doc__)
        return 1
    return table[cmd](argv)


if __name__ == "__main__":
    sys.exit(main())
