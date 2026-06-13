from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os


LISTEN_HOST = os.environ.get("AI_DEMO_HOST", "0.0.0.0")
LISTEN_PORT = int(os.environ.get("AI_DEMO_PORT", "18002"))

DEMO_LABELS = [
    ("chair", 8, "Ghế"),
    ("sofa", 39, "Sofa"),
    ("automatic_washer", 51, "Máy giặt"),
    ("television_set", 47, "Tivi"),
    ("dining_table", 13, "Bàn ăn"),
    ("table_lamp", 45, "Đèn bàn"),
]


class AiDemoHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        if self.path == "/health":
            self._send_json({
                "status": "healthy",
                "service": "ai-service",
                "model_loaded": True,
                "device": "demo",
                "model_path": "demo-mode",
                "architecture": "H-Smart Demo Vision",
                "score_threshold": 0.4,
                "demo_mode": True,
            })
            return
        self.send_error(404, "Not found")

    def do_POST(self):
        if self.path != "/api/v1/predict":
            self.send_error(404, "Not found")
            return

        content_length = int(self.headers.get("Content-Length", "0") or "0")
        body = self.rfile.read(content_length)
        if not body:
            self.send_error(400, "Empty file")
            return

        label, class_id, translated_label = DEMO_LABELS[sum(body[:4096]) % len(DEMO_LABELS)]
        detection = {
            "label": label,
            "class_id": class_id,
            "score": 0.91,
            "bbox": [],
            "translated_label": translated_label,
        }
        self._send_json({
            "label": label,
            "confidence": 0.91,
            "translated_label": translated_label,
            "num_detections": 1,
            "detections": [detection],
        })

    def _send_json(self, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        print(f"{self.address_string()} - {format % args}", flush=True)


if __name__ == "__main__":
    server = ThreadingHTTPServer((LISTEN_HOST, LISTEN_PORT), AiDemoHandler)
    print(f"H-Smart AI demo service listening on {LISTEN_HOST}:{LISTEN_PORT}", flush=True)
    server.serve_forever()
