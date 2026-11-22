"""Exercise the Nginx template against a synthetic TLS upstream on loopback."""

import argparse
import base64
from contextlib import closing
import hashlib
from http.client import HTTPConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import secrets
import socket
import ssl
import subprocess
from tempfile import TemporaryDirectory
from threading import Thread
import time
from urllib.parse import parse_qs, urlsplit


LOOPBACK = "127.0.0.1"
ORIGIN = "https://crm.rutafija.test"


def render(shell, nginx_files, settings):
    allowed = ("PATH", "SystemRoot", "WINDIR", "TEMP", "TMP", "HOME")
    environment = {name: os.environ[name] for name in allowed if name in os.environ}
    result = subprocess.run([str(shell), "-c", '. "$1" && env', "rf", str(nginx_files / "15-ruta-fija-proxy.envsh")],
                            env=environment | settings, capture_output=True, text=True, check=True)
    values = dict(line.split("=", 1) for line in result.stdout.splitlines()
                  if line.startswith("RF_PROXY_") and "=" in line)
    return re.sub(r"\$\{(RF_PROXY_[A-Z_]+)\}", lambda match: values[match[1]],
                  (nginx_files / "default.conf.template").read_text(encoding="utf-8"))


def request(port, method, path, **headers):
    with closing(HTTPConnection(LOOPBACK, port, timeout=5)) as connection:
        connection.request(method, path, headers=headers)
        response = connection.getresponse()
        return response.status, dict(response.getheaders()), response.read()


def run(args):
    nginx_files = args.project.resolve(strict=True) / "frontend/nginx"
    ticket = secrets.token_urlsafe(24)
    names = []

    class Upstream(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, *args):
            pass

        def respond(self, status, body=b"", **headers):
            self.send_response(status)
            self.send_header("Content-Length", str(len(body)))
            for name, value in headers.items():
                self.send_header(name.replace("_", "-"), value)
            self.end_headers()
            self.wfile.write(body)

        def do_POST(self):
            if self.path != "/api/v1/auth/login" or self.headers.get("Origin") != ORIGIN:
                self.respond(403)
                return
            self.respond(200, b"{}", Set_Cookie="synthetic=fixture; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict")

        def do_GET(self):
            parsed = urlsplit(self.path)
            if parsed.path == "/ws/operations":
                if (parse_qs(parsed.query).get("ticket") != [ticket] or self.headers.get("Origin") != ORIGIN
                        or self.headers.get("Upgrade", "").lower() != "websocket" or self.server.consumed):
                    self.respond(403)
                    return
                self.server.consumed = True
                key = self.headers["Sec-WebSocket-Key"] + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
                accept = base64.b64encode(hashlib.sha1(key.encode()).digest()).decode()
                self.send_response(101)
                self.send_header("Connection", "Upgrade")
                self.send_header("Upgrade", "websocket")
                self.send_header("Sec-WebSocket-Accept", accept)
                self.end_headers()
                self.close_connection = True
                return
            if self.headers.get("X-Fixture-Tenant") != "allowed":
                self.respond(403)
                return
            self.respond(200, json.dumps({name: self.headers.get(name) for name in
                                         ("Host", "X-Forwarded-Host", "X-Forwarded-Proto",
                                          "X-Forwarded-Port", "Forwarded")}).encode())

    default = render(args.shell, nginx_files, {})
    assert default.count("proxy_pass http://backend:8080;") == 2 and "listen 80;" in default
    for invalid in ({"RF_PROXY_UPSTREAM_HOST": "localhost;return"},
                    {"RF_PROXY_UPSTREAM_PORT": "65536"}, {"RF_PROXY_UPSTREAM_SCHEME": "ftp"}):
        try:
            render(args.shell, nginx_files, invalid)
        except subprocess.CalledProcessError:
            pass
        else:
            raise AssertionError("An unsafe proxy value was accepted")
    print("PASS Compose defaults and invalid configuration")

    with TemporaryDirectory(prefix="rf-nginx-") as directory, ThreadingHTTPServer((LOOPBACK, 0), Upstream) as upstream:
        root = Path(directory)
        (root / "logs").mkdir()
        (root / "temp").mkdir()
        (root / "public/config").mkdir(parents=True)
        (root / "public/index.html").write_text("synthetic SPA", encoding="ascii")
        (root / "public/config/runtime-config.json").write_text('{"apiBaseUrl":"/api/v1"}', encoding="ascii")
        certificate, key = root / "certificate.pem", root / "key.pem"
        subprocess.run([str(args.openssl), "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
                        "-subj", "/CN=localhost", "-addext", "subjectAltName=DNS:localhost",
                        "-keyout", str(key), "-out", str(certificate)],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(certificate, key)
        context.set_servername_callback(lambda _, name, __: names.append(name))
        upstream.socket = context.wrap_socket(upstream.socket, server_side=True)
        upstream.consumed = False
        thread = Thread(target=upstream.serve_forever, daemon=True)
        thread.start()
        try:
            with socket.socket() as local:
                local.bind((LOOPBACK, 0))
                port = local.getsockname()[1]
            config = render(args.shell, nginx_files, {"PORT": str(port), "RF_PROXY_UPSTREAM_HOST": "localhost",
                            "RF_PROXY_UPSTREAM_SCHEME": "https", "RF_PROXY_UPSTREAM_PORT": str(upstream.server_port),
                            "RF_PROXY_PUBLIC_SCHEME": "https"})
            config = config.replace(f"listen {port};", f"listen {LOOPBACK}:{port};").replace(f"listen [::]:{port};", "")
            config = config.replace("/usr/share/nginx/html", f'"{(root / "public").as_posix()}"')
            config = config.replace("/etc/ssl/certs/ca-certificates.crt", f'"{certificate.as_posix()}"')
            if os.name == "nt":
                config = config.replace("/dev/null", "NUL")
            (root / "nginx.conf").write_text(
                "daemon off; master_process off; error_log logs/error.log;\n"
                "events {} http { access_log logs/access.log;\n" + config + "\n}\n", encoding="utf-8")
            command = [str(args.nginx), "-p", root.as_posix() + "/", "-c", "nginx.conf"]
            subprocess.run([*command, "-t"], cwd=root, check=True)
            with subprocess.Popen(command, cwd=root, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL) as nginx:
                try:
                    for _ in range(40):
                        if nginx.poll() is not None:
                            raise RuntimeError("Nginx stopped before health was available")
                        try:
                            if request(port, "GET", "/health")[0] == 200:
                                break
                        except OSError:
                            time.sleep(0.15)
                    assert request(port, "GET", "/assignments")[2] == b"synthetic SPA"
                    status, headers, body = request(port, "GET", "/config/runtime-config.json")
                    assert status == 200 and json.loads(body)["apiBaseUrl"] == "/api/v1" and "no-store" in headers["Cache-Control"]
                    status, headers, _ = request(port, "POST", "/api/v1/auth/login", Origin=ORIGIN)
                    assert status == 200 and "; HttpOnly; Secure; SameSite=Strict" in headers["Set-Cookie"]
                    status, _, body = request(port, "GET", "/api/v1/probe", Host="crm.rutafija.test",
                                              **{"X-Fixture-Tenant": "allowed", "Forwarded": "proto=http"})
                    assert status == 200 and json.loads(body) == {"Host": f"localhost:{upstream.server_port}",
                           "X-Forwarded-Host": "crm.rutafija.test", "X-Forwarded-Proto": "https",
                           "X-Forwarded-Port": "443", "Forwarded": None}
                    assert request(port, "GET", "/api/v1/probe", **{"X-Fixture-Tenant": "other"})[0] == 403
                    ws = {"Upgrade": "websocket", "Connection": "Upgrade",
                          "Sec-WebSocket-Version": "13", "Sec-WebSocket-Key": base64.b64encode(secrets.token_bytes(16)).decode()}
                    path = "/ws/operations?ticket=" + ticket
                    assert request(port, "GET", path, Origin="https://outside.rutafija.test", **ws)[0] == 403
                    assert request(port, "GET", path, Origin=ORIGIN, **ws)[0] == 101
                    assert request(port, "GET", path, Origin=ORIGIN, **ws)[0] == 403
                    assert "localhost" in names
                finally:
                    nginx.terminate()
                    nginx.wait(timeout=10)
            assert all(ticket not in path.read_text(errors="replace") for path in (root / "logs").glob("*.log"))
            print("PASS Nginx HTTPS upstream/SNI, SPA, config, cookie, forwarded headers, WebSocket 101 and sanitized logs")
        finally:
            upstream.shutdown()
            thread.join(timeout=5)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("project", "nginx", "shell", "openssl"):
        parser.add_argument("--" + name, type=Path, required=True)
    run(parser.parse_args())
