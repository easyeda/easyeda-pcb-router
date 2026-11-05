#!/usr/bin/env python3
"""Packaged HTTP/WebSocket and worker lifecycle checks; Python standard library only."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import socket
import struct
import subprocess
import tempfile
import time
import urllib.request


class WebSocket:
    def __init__(self, port):
        self.sock = socket.create_connection(("127.0.0.1", port), timeout=15)
        key = base64.b64encode(os.urandom(16)).decode()
        self.sock.sendall(("GET /router HTTP/1.1\r\nHost: 127.0.0.1:%d\r\n"
                           "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                           "Sec-WebSocket-Version: 13\r\nSec-WebSocket-Key: %s\r\n\r\n" % (port, key)).encode())
        headers = b""
        while not headers.endswith(b"\r\n\r\n"):
            headers += self.exact(1)
        assert b" 101 " in headers, headers
        accept = base64.b64encode(hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()).digest())
        assert accept in headers, "WebSocket handshake hash"

    def exact(self, count):
        result = b""
        while len(result) < count:
            data = self.sock.recv(count - len(result))
            if not data:
                raise EOFError("WebSocket closed")
            result += data
        return result

    def frame(self, payload, opcode=1):
        mask = os.urandom(4)
        size = len(payload)
        header = bytes([128 | opcode, 128 | (size if size < 126 else 126 if size < 65536 else 127)])
        if size >= 126:
            header += struct.pack("!H" if size < 65536 else "!Q", size)
        self.sock.sendall(header + mask + bytes(c ^ mask[i % 4] for i, c in enumerate(payload)))

    def send(self, value):
        self.frame(json.dumps(value).encode())

    def recv(self):
        message = b""
        while True:
            first, second = self.exact(2)
            size = second & 127
            if size >= 126:
                size = struct.unpack("!H" if size == 126 else "!Q", self.exact(2 if size == 126 else 8))[0]
            assert size <= 80000000, "Oversized WebSocket message"
            data = self.exact(size)
            opcode = first & 15
            if opcode == 9:
                self.frame(data, 10)
                continue
            if opcode == 8:
                raise EOFError("WebSocket close frame")
            assert opcode in (0, 1), opcode
            message += data
            if first & 128:
                return json.loads(message)

    def close(self):
        try:
            self.frame(b"", 8)
        except OSError:
            pass
        self.sock.close()


def request(dsn, timeout=10, progress=1):
    return dict(a="startRoute", data=dsn, timeout=timeout, progressInterval=progress, optimizeTime=1)


def result(ws):
    deadline = time.monotonic() + 20
    events = []
    while time.monotonic() < deadline:
        event = ws.recv()
        events.append(event)
        if event["a"] == "routingResult":
            if event["complete"] >= 0:
                assert (event["complete"] == 1) == (event["inCompleteNetNum"] == 0), event
            return event, events
    raise AssertionError("No terminal result")


def no_duplicate(ws):
    ws.sock.settimeout(.4)
    try:
        raise AssertionError("Duplicate response: %r" % ws.recv())
    except socket.timeout:
        pass
    finally:
        ws.sock.settimeout(15)


def children(pid):
    output = subprocess.check_output(["ps", "-axo", "pid=,ppid=,command="], text=True)
    return [int(row.split(None, 2)[0]) for row in output.splitlines()
            if len(row.split(None, 2)) == 3 and row.split(None, 2)[1] == str(pid)
            and "easyeda-worker.jar" in row]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("distribution", type=Path)
    parser.add_argument("--diagnostics", action="store_true")
    args = parser.parse_args()
    source = args.distribution.resolve()
    board = Path("testcase/test.dsn").read_text()
    large = Path("testcase/MAIN.dsn").read_text()
    with tempfile.TemporaryDirectory(prefix="easyeda packaged checks ") as temporary:
        distribution = Path(temporary) / "Router with spaces"
        shutil.copytree(source, distribution)
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", 0))
            port = probe.getsockname()[1]
        config = distribution / "config/local/main.json"
        cfg = json.loads(config.read_text())
        cfg["web"]["port"] = port
        cfg["router"].update(engine="modern", min_timeout=1000, min_progress_interval=100,
                             keep_heartbeat=500, max_active_jobs=1, max_queued_jobs=1,
                             worker_threads=2, worker_heap_mb=512, worker_stop_grace_ms=3000)
        config.write_text(json.dumps(cfg))
        env = os.environ.copy()
        env["EASYEDA_ROUTER_ENGINE"] = "modern"
        env.pop("EASYEDA_ROUTER_DIAGNOSTICS", None)
        if args.diagnostics:
            env["EASYEDA_ROUTER_DIAGNOSTICS"] = str(Path(temporary) / "captures")
        log = (Path(temporary) / "server.log").open("w")
        process = subprocess.Popen(["sh", str(distribution / "mac64.sh")], cwd=temporary,
                                   env=env, stdin=subprocess.DEVNULL, stdout=log, stderr=log)
        sockets = []

        def connect():
            ws = WebSocket(port)
            sockets.append(ws)
            return ws

        try:
            for _ in range(100):
                try:
                    with urllib.request.urlopen("http://127.0.0.1:%d/api/whois" % port, timeout=.2) as health:
                        assert health.read() == b"EasyEDA Auto Router"
                    break
                except OSError:
                    if process.poll() is not None:
                        raise AssertionError((Path(temporary) / "server.log").read_text())
                    time.sleep(.1)
            else:
                raise AssertionError("Server did not start")
            print("Packaged launcher, spaces in paths, HTTP health: passed", flush=True)

            ws = connect()
            for packet in [request("invalid"), request("(pcb invalid)"), request(board, timeout=-1),
                           request(board, timeout=2147483647), request(board, progress=-1),
                           dict(request(board), optimizeTime=1.5)]:
                ws.send(packet)
                final, _ = result(ws)
                assert final["complete"] == -2, final
                no_duplicate(ws)
            ws.close()
            print("Invalid DSN/requests and single terminal errors: passed", flush=True)

            ws = connect()
            ws.send(request(board))
            final, events = result(ws)
            assert final["complete"] == 1, final
            assert final["data"] and any(e["a"] == "routingProgress" for e in events), events
            assert any(e["a"] == "heartbeat" for e in events), events
            assert all(wire["layerid"] in (1, 2) for net in final["data"].values() for wire in net["wires"])
            if args.diagnostics:
                matches = []
                for path in (Path(temporary) / "captures").glob("route-*/result.json"):
                    recorded = json.loads(path.read_text())
                    if recorded.get("complete") == 1:
                        matches.append(path)
                assert len(matches) == 1, matches
                capture = matches[0].parent
                assert json.loads(matches[0].read_text()) == final, "Captured browser packet differs"
                assert (capture / "input.dsn").read_text() == board, "Captured DSN differs"
                assert "(network_out" in (capture / "result.ses").read_text(), "Raw SES missing"
                assert (capture / "engine.txt").read_text().strip() == "modern"
                print("Exact input, raw SES, and browser-result diagnostics: passed", flush=True)
            no_duplicate(ws)
            ws.close()
            print("Actual modern routing, heartbeat, progress, geometry: passed", flush=True)

            ws = connect()
            ws.send(request(large, timeout=1))
            final, _ = result(ws)
            assert final["complete"] == 0 and final["data"], final
            no_duplicate(ws)
            ws.close()
            print("Timeout returns consistent partial geometry: passed", flush=True)

            ws = connect()
            ws.send(request(large, timeout=30))
            assert ws.recv()["a"] == "heartbeat"
            ws.send(request(board))
            final, _ = result(ws)
            assert final["complete"] == 1, final
            no_duplicate(ws)
            ws.close()
            print("Replacement cancels old job without stale final callbacks: passed", flush=True)

            ws = connect()
            ws.send(request(large, timeout=30))
            while ws.recv()["a"] != "routingProgress":
                pass
            running = children(process.pid)
            assert len(running) == 1, running
            os.kill(running[0], signal.SIGKILL)
            final, _ = result(ws)
            assert final["complete"] == -3, final
            no_duplicate(ws)
            ws.close()
            print("Worker crash during routing returns one engine error: passed", flush=True)

            first, queued, overflow = connect(), connect(), connect()
            first.send(request(large, timeout=30))
            assert first.recv()["a"] == "heartbeat"
            queued.send(request(large, timeout=30))
            # Ensure the queued message has been admitted before checking overflow.
            time.sleep(.3)
            overflow.send(request(board))
            final, _ = result(overflow)
            assert final["complete"] == -1, final
            queued.close()
            time.sleep(.3)
            overflow.send(request(board))
            first.close()
            final, _ = result(overflow)
            assert final["complete"] == 1, final
            overflow.close()
            print("Bounded queue, busy code, queued disconnect/removal, recovery: passed", flush=True)

            ws = connect()
            ws.send(request(large, timeout=30))
            assert ws.recv()["a"] == "heartbeat"
            time.sleep(1)
            worker_pids = children(process.pid)
            assert len(worker_pids) <= 1, worker_pids
            process.terminate()
            process.wait(timeout=12)
            assert not children(process.pid), "Worker survived facade shutdown"
            for pid in worker_pids:
                try:
                    os.kill(pid, 0)
                except ProcessLookupError:
                    continue
                raise AssertionError("Worker still alive after shutdown: %d" % pid)
            print("Worker concurrency bound and shutdown cleanup: passed", flush=True)

            # Startup validation must fail before serving work; never silently fall back.
            java_home = env.get("JAVA_HOME")
            facade_java = str(Path(java_home) / "bin/java") if java_home else shutil.which("java")

            def startup_failure(properties, expected):
                output = subprocess.run([facade_java, "-Dcom.easyeda.env=local",
                                         "-Dcom.easyeda.router.engine=modern"] + properties
                                        + ["-jar", "bin/bootstrap.jar"], cwd=distribution,
                                        stdin=subprocess.DEVNULL, capture_output=True, text=True, timeout=10)
                assert output.returncode != 0, output.stdout + output.stderr
                assert expected in output.stdout + output.stderr, output.stdout + output.stderr

            startup_failure(["-Dcom.easyeda.freerouting.java=" + str(distribution / "missing-java")], "missing-java")
            old_java = Path(temporary) / "old-java"
            old_java.write_text('#!/bin/sh\necho \'openjdk version "17.0.1"\'\n')
            old_java.chmod(0o755)
            startup_failure(["-Dcom.easyeda.freerouting.java=" + str(old_java)], "requires Java 25")
            core = distribution / "engine/freerouting-2.5.0.jar"
            with core.open("r+b") as stream:
                original = stream.read(1)
                stream.seek(0)
                stream.write(b"X")
            startup_failure([], "checksum mismatch")
            with core.open("r+b") as stream:
                stream.write(original)
            worker = distribution / "engine/easyeda-worker.jar"
            worker.rename(worker.with_suffix(".disabled"))
            startup_failure([], "Modern worker missing")
            print("Missing/wrong runtime, corrupt core, missing worker fail fast: passed", flush=True)
        except BaseException:
            log.flush()
            print((Path(temporary) / "server.log").read_text()[-12000:])
            raise
        finally:
            for ws in sockets:
                ws.close()
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=12)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
            log.close()


if __name__ == "__main__":
    main()
