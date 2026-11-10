"""Exercise the built server over local HTTP with disposable data and credentials."""
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

root = Path(__file__).resolve().parent
with tempfile.TemporaryDirectory(prefix="reading-dog-routes-") as folder:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    token = secrets.token_hex(32)
    env = dict(os.environ, ACCESS_TOKEN=token, PORT=str(port), DATA_DIR=folder)
    classpath = os.pathsep.join([str(root / "build/classes"), str(root / "build/lib/*")])
    process = subprocess.Popen(
        ["java", "-cp", classpath, "com.example.yuejie.Server"], env=env,
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    try:
        def request(path, body=None, auth=True):
            headers = {"Authorization": "Bearer " + token} if auth else {}
            req = urllib.request.Request(
                f"http://127.0.0.1:{port}{path}", headers=headers,
                data=None if body is None else json.dumps(body).encode(),
            )
            try:
                with urllib.request.urlopen(req, timeout=3) as response:
                    return response.status, json.load(response)
            except urllib.error.HTTPError as error:
                return error.code, {}

        for attempt in range(60):
            try:
                assert request("/health", auth=False)[0] == 200
                break
            except urllib.error.URLError:
                if process.poll() is not None or attempt == 59:
                    raise
                time.sleep(0.1)
        assert request("/api/v1/time")[0] == 200
        assert request("/api/v1/time", auth=False)[0] == 401
        for route in ["search", "detail", "directory", "chapter", "page-metadata",
                      "comments/metadata", "comments/stats", "comments/page"]:
            assert request("/api/v1/" + route, {})[0] == 404, route
        assert request("/api/v1/media?url=https%3A%2F%2Fp3.byteimg.com%2Fa")[0] == 404
        assert request("/api/v1/sync", {
            "device": "route-fixture", "cursor": 0, "operations": []
        })[0] == 200
        print("PASS health, auth, time, sync; all 9 content/media routes absent")
    finally:
        process.terminate()
        process.wait(timeout=10)
