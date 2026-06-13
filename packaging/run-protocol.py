#!/usr/bin/env python3
"""K5 orchestrator: boot the Fabric server, run the protocol-aware client, and
report the verdict for the rock-protocol on-the-wire round trip."""
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SERVER_DIR = ROOT / "server"
JAVA = "/usr/lib/jvm/java-21-temurin/bin/java"


def main() -> int:
    log = open(SERVER_DIR / "protocol-server.log", "w")
    server = subprocess.Popen(
        [JAVA, "-Xmx3g", "-jar", "fabric-server.jar", "nogui"],
        cwd=SERVER_DIR, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True, bufsize=1)

    booted = False
    deadline = time.time() + 300
    for line in server.stdout:
        log.write(line)
        log.flush()
        if "rock:protocol" in line or "Protocol hub active" in line:
            print(f"[server] {line.rstrip()}", flush=True)
        if "Done (" in line:
            booted = True
            break
        if time.time() > deadline or server.poll() is not None:
            break
    if not booted:
        print("[protocol] server failed to boot — see protocol-server.log", flush=True)
        server.kill()
        return 2

    print("[protocol] server up — launching protocol client", flush=True)

    def console(cmd: str) -> None:
        print(f"[console] {cmd}", flush=True)
        server.stdin.write(cmd + "\n")
        server.stdin.flush()

    bot = subprocess.Popen(
        ["node", "protocol-bot.js"], cwd=ROOT / "bots",
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
    for line in bot.stdout:
        print(f"[bot] {line.rstrip()}", flush=True)
        if "AWAITING_GRANTS" in line:
            # Grant the capability-backing permissions before the bot handshakes.
            console("rock perms grant ProtoBot rock.client.claims")
            console("rock perms grant ProtoBot rock.client.wallet")
    rc = bot.wait()

    server.stdin.write("stop\n")
    server.stdin.flush()
    try:
        server.wait(timeout=60)
    except subprocess.TimeoutExpired:
        server.kill()
    log.close()
    print(f"[protocol] client exit code: {rc}", flush=True)
    return rc


if __name__ == "__main__":
    sys.exit(main())
