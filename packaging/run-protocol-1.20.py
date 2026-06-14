#!/usr/bin/env python3
"""Multi-version check: boot the real Fabric 1.20.6 server with the retargeted
adapter + version-agnostic platform, and run the protocol-aware client — proving
the matrix end-to-end on the 1.20.x family (not just compile)."""
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SERVER_DIR = ROOT / "server-1.20"
JAVA = "/usr/lib/jvm/java-21-temurin/bin/java"
PORT = "25567"


def main() -> int:
    log = open(SERVER_DIR / "protocol-server.log", "w")
    server = subprocess.Popen(
        [JAVA, "-Xmx3g", "-jar", "fabric-server.jar", "nogui"],
        cwd=SERVER_DIR, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True, bufsize=1)

    def console(cmd):
        print(f"[console] {cmd}", flush=True)
        server.stdin.write(cmd + "\n")
        server.stdin.flush()

    booted = False
    deadline = time.time() + 300
    for line in server.stdout:
        log.write(line)
        log.flush()
        if "Protocol hub active" in line:
            print(f"[server] {line.rstrip()}", flush=True)
        if "Done (" in line:
            booted = True
            break
        if time.time() > deadline or server.poll() is not None:
            break
    if not booted:
        print("[protocol] 1.20.6 server failed to boot — see protocol-server.log", flush=True)
        server.kill()
        return 2

    print("[protocol] 1.20.6 server up — launching protocol client", flush=True)
    env = dict(os.environ, PROTO_PORT=PORT, PROTO_VERSION="1.20.6")
    bot = subprocess.Popen(
        ["node", "protocol-bot.js"], cwd=ROOT / "bots", env=env,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
    for line in bot.stdout:
        print(f"[bot] {line.rstrip()}", flush=True)
        if "AWAITING_GRANTS" in line:
            console("rock perms grant ProtoBot rock.client.claims")
            console("rock perms grant ProtoBot rock.client.wallet")
    rc = bot.wait()

    console("stop")
    try:
        server.wait(timeout=60)
    except subprocess.TimeoutExpired:
        server.kill()
    log.close()
    print(f"[protocol] client exit code: {rc}", flush=True)
    return rc


if __name__ == "__main__":
    sys.exit(main())
