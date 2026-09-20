"""极简 RESP 客户端：不依赖第三方库，用于验证 Redis 连通性与读写。"""
import socket
import sys

HOST = "rxs"
PORT = 6379
DB = 2


class MiniRedis:
    def __init__(self, host, port, db=0, timeout=5):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.fp = self.sock.makefile("rb")
        if db:
            self.cmd("SELECT", db)

    def _send(self, args):
        out = [b"*%d\r\n" % len(args)]
        for a in args:
            if not isinstance(a, bytes):
                a = str(a).encode("utf-8")
            out.append(b"$%d\r\n" % len(a) + a + b"\r\n")
        self.sock.sendall(b"".join(out))

    def _read(self):
        line = self.fp.readline()
        if not line:
            raise ConnectionError("connection closed")
        t, body = line[:1], line[1:-2]
        if t == b"+":
            return body.decode("utf-8")
        if t == b"-":
            raise RuntimeError("redis error: " + body.decode("utf-8"))
        if t == b":":
            return int(body)
        if t == b"$":
            n = int(body)
            if n == -1:
                return None
            data = self.fp.read(n + 2)[:-2]
            return data.decode("utf-8", "replace")
        if t == b"*":
            n = int(body)
            if n == -1:
                return None
            return [self._read() for _ in range(n)]
        raise RuntimeError("unknown reply type: %r" % t)

    def cmd(self, *args):
        self._send(args)
        return self._read()

    def close(self):
        try:
            self.fp.close()
            self.sock.close()
        except Exception:
            pass


def main():
    r = MiniRedis(HOST, PORT, DB)
    print("PING ->", r.cmd("PING"))
    print("SELECT db ->", DB)
    r.cmd("SET", "__probe_key__", "hello-from-workbuddy", "EX", 30)
    print("GET ->", r.cmd("GET", "__probe_key__"))
    print("TTL ->", r.cmd("TTL", "__probe_key__"))
    r.cmd("DEL", "__probe_key__")
    info = r.cmd("INFO", "server")
    for line in info.splitlines():
        if line.startswith(("redis_version", "os", "tcp_port", "uptime_in_seconds")):
            print("INFO:", line.strip())
    print("dbsize(db%d) ->" % DB, r.cmd("DBSIZE"))
    r.close()
    print("PROBE_OK")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print("PROBE_FAIL: %s: %s" % (type(e).__name__, e))
        sys.exit(1)
