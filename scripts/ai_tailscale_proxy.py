from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import os


TARGET_BASE_URL = os.environ.get("AI_PROXY_TARGET", "http://127.0.0.1:8002").rstrip("/")
LISTEN_HOST = os.environ.get("AI_PROXY_HOST", "0.0.0.0")
LISTEN_PORT = int(os.environ.get("AI_PROXY_PORT", "18002"))


class ProxyHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        self._proxy()

    def do_POST(self):
        self._proxy()

    def _proxy(self):
        target_url = f"{TARGET_BASE_URL}{self.path}"
        body = self._read_request_body()
        headers = {
            key: value
            for key, value in self.headers.items()
            if key.lower() not in {"host", "content-length", "connection", "accept-encoding", "transfer-encoding"}
        }

        request = Request(target_url, data=body if body else None, headers=headers, method=self.command)
        try:
            with urlopen(request, timeout=180) as response:
                response_body = response.read()
                self._send_response(response.status, response.headers, response_body)
        except HTTPError as error:
            self._send_response(error.code, error.headers, error.read())
        except URLError as error:
            message = f"AI proxy target unavailable: {error.reason}".encode("utf-8")
            self.send_response(502)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(message)))
            self.end_headers()
            self.wfile.write(message)

    def _read_request_body(self):
        transfer_encoding = self.headers.get("Transfer-Encoding", "").lower()
        if "chunked" in transfer_encoding:
            chunks = []
            while True:
                size_line = self.rfile.readline().strip()
                if not size_line:
                    continue
                chunk_size = int(size_line.split(b";", 1)[0], 16)
                if chunk_size == 0:
                    self.rfile.readline()
                    break
                chunks.append(self.rfile.read(chunk_size))
                self.rfile.read(2)
            return b"".join(chunks)

        return self.rfile.read(int(self.headers.get("Content-Length", "0") or "0"))

    def _send_response(self, status, headers, body):
        self.send_response(status)
        for key, value in headers.items():
            if key.lower() not in {"transfer-encoding", "connection", "content-length"}:
                self.send_header(key, value)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        print(f"{self.address_string()} - {format % args}", flush=True)


if __name__ == "__main__":
    server = ThreadingHTTPServer((LISTEN_HOST, LISTEN_PORT), ProxyHandler)
    print(f"AI proxy listening on {LISTEN_HOST}:{LISTEN_PORT} -> {TARGET_BASE_URL}", flush=True)
    server.serve_forever()
