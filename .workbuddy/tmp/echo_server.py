"""临时回显服务：把收到的请求头原样以 JSON 返回。

用途：端到端验证「网关鉴权通过后，注入的 X-User-Id / X-Username / X-User-Nickname
是否真的被转发给了下游」。由网关临时路由指向本服务，不参与真实业务。
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer


class EchoHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _echo(self):
        raw = b""
        length = self.headers.get("Content-Length")
        if length:
            try:
                raw = self.rfile.read(int(length))
            except Exception:
                raw = b""
        payload = {
            "method": self.command,
            "path": self.path,
            "headers": {k: v for k, v in self.headers.items()},
            "headerValues": {},
            "body": raw.decode("utf-8", "replace"),
        }
        for name in self.headers.keys():
            payload["headerValues"][name] = self.headers.get_all(name)
        body = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    do_GET = do_POST = do_PUT = do_DELETE = do_PATCH = _echo

    def log_message(self, fmt, *args):
        sys.stderr.write("[echo] %s\n" % (fmt % args))


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9099
    HTTPServer(("127.0.0.1", port), EchoHandler).serve_forever()
