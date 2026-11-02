#!/usr/bin/env python3
"""Play Thirteen against networks and other players, or analyze hands, in the browser.

    python python/play_gui.py --models run/models [--human human.txt] [--port 8767]

Starts one `thirteen gui` engine process and a local web server (127.0.0.1), then opens gui/play.html. Python 3
standard library only. The page talks to the engine through POST /api/engine (one JSON request per call, see
cpp/command/gui.cpp); the engine is the only rules authority.
"""

import argparse
import glob
import http.server
import json
import os
import re
import subprocess
import sys
import threading
import urllib.parse
import webbrowser

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
GUI = os.path.join(REPO, "gui")
STATIC = {"/": "play.html", "/play.html": "play.html", "/thirteen-eval.js": "thirteen-eval.js"}
CONTENT_TYPES = {".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8"}
COMMANDS = ("deal", "arrange", "analyze", "mc", "score")


class Engine:
    """One `thirteen gui` process; requests are serialized. Restarted after a crash."""

    def __init__(self, binary):
        self.binary = binary
        self.lock = threading.Lock()
        self.proc = None

    def _start(self):
        self.proc = subprocess.Popen([self.binary, "gui"], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     text=True, bufsize=1)

    def request(self, req):
        with self.lock:
            if self.proc is None or self.proc.poll() is not None:
                self._start()
            self.proc.stdin.write(json.dumps(req) + "\n")
            self.proc.stdin.flush()
            line = self.proc.stdout.readline()
            if not line:
                self.proc = None
                return {"error": "The engine stopped; it will restart on the next request"}
            return json.loads(line)

    def close(self):
        if self.proc is not None and self.proc.poll() is None:
            self.proc.stdin.close()
            self.proc.wait(timeout=5)


def find_models(dirs):
    """(name, path) of every .onnx under the given directories, in iteration order (net_2 before net_10)."""
    models = []
    for d in dirs:
        for path in glob.glob(os.path.join(d, "*.onnx")):
            models.append((os.path.splitext(os.path.basename(path))[0], os.path.abspath(path)))
    key = lambda m: [int(t) if t.isdigit() else t for t in re.split(r"(\d+)", m[0])]
    return sorted(models, key=key)


def make_config(models, human):
    players = [{"name": name, "spec": f"net:model={path}"} for name, path in reversed(models)]
    if human:
        players.append({"name": "human model", "spec": f"human:weights={os.path.abspath(human)}"})
        players.append({"name": "human model (argmax)", "spec": f"human:weights={os.path.abspath(human)},temp=0"})
    players += [{"name": "indep", "spec": "indep"}, {"name": "greedy", "spec": "greedy"},
                {"name": "random", "spec": "random"}, {"name": "mc 200 vs greedy", "spec": "mc:samples=200"}]
    return {"models": [{"name": name, "path": path} for name, path in reversed(models)], "players": players,
            "has_human": bool(human)}


def make_handler(engine, config):
    class Handler(http.server.BaseHTTPRequestHandler):
        server_version = "ThirteenPlay/1"

        def log_message(self, *a):
            pass

        def _send(self, status, body, ctype="application/json; charset=utf-8"):
            if not isinstance(body, bytes):
                body = body.encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            path = urllib.parse.urlparse(self.path).path
            if path == "/api/config":
                return self._send(200, json.dumps(config))
            rel = STATIC.get(path)
            if rel is None:
                return self._send(404, "not found", "text/plain; charset=utf-8")
            with open(os.path.join(GUI, rel), "rb") as f:
                self._send(200, f.read(), CONTENT_TYPES[os.path.splitext(rel)[1]])

        def do_POST(self):
            # Only JSON bodies: a cross-site form or no-cors fetch cannot send application/json without a CORS
            # preflight, which this server never approves.
            if urllib.parse.urlparse(self.path).path != "/api/engine":
                return self._send(404, json.dumps({"error": "unknown endpoint"}))
            if not self.headers.get("Content-Type", "").startswith("application/json"):
                return self._send(415, json.dumps({"error": "expected application/json"}))
            try:
                n = int(self.headers.get("Content-Length") or 0)
                req = json.loads(self.rfile.read(n) or b"{}")
                if not isinstance(req, dict) or req.get("cmd") not in COMMANDS:
                    raise ValueError("expected {\"cmd\": one of %s, ...}" % ", ".join(COMMANDS))
            except ValueError as e:
                return self._send(400, json.dumps({"error": str(e)}))
            answer = engine.request(req)
            self._send(400 if "error" in answer else 200, json.dumps(answer))

    return Handler


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--binary", default=os.path.join(REPO, "cpp", "build", "thirteen"),
                    help="thirteen executable built with ONNX Runtime")
    ap.add_argument("--models", action="append", default=[], help="Directory of .onnx models (repeatable)")
    ap.add_argument("--human", help="Human model weights (python/train_human.py output)")
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=8767)
    ap.add_argument("--no-browser", action="store_true")
    args = ap.parse_args()

    if not os.access(args.binary, os.X_OK):
        ap.error("not an executable: " + args.binary)
    models = find_models(args.models)
    if not models:
        print("No models found; only the built-in players are available", file=sys.stderr)
    engine = Engine(os.path.abspath(args.binary))
    server = http.server.ThreadingHTTPServer((args.host, args.port), make_handler(engine, make_config(models, args.human)))
    url = f"http://{args.host}:{server.server_address[1]}/"
    print(f"Thirteen play GUI at {url}  ({len(models)} models; Ctrl+C to stop)", flush=True)
    if not args.no_browser:
        webbrowser.open(url)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        engine.close()


if __name__ == "__main__":
    main()
