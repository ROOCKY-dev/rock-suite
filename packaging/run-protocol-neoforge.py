#!/usr/bin/env python3
"""K5 (NeoForge): boot the real NeoForge server, run the protocol-aware client
against it, and report the rock-protocol on-the-wire verdict — the NeoForge
counterpart to run-protocol.py."""
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SERVER = ROOT / "server-neoforge"
JAVA = "/usr/lib/jvm/java-21-temurin/bin/java"
ARGS = SERVER / "libraries/net/neoforged/neoforge/21.11.42/unix_args.txt"
PORT = "25566"  # server-neoforge/server.properties


def main() -> int:
    log = open(SERVER / "protocol-server.log", "w")
    server = subprocess.Popen(
        [JAVA, "@user_jvm_args.txt", f"@{ARGS}", "nogui"],
        cwd=SERVER, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True, bufsize=1)

    def console(cmd: str) -> None:
        print(f"[console] {cmd}", flush=True)
        server.stdin.write(cmd + "\n")
        server.stdin.flush()

    booted = False
    deadline = time.time() + 300
    for line in server.stdout:
        log.write(line)
        log.flush()
        if "Done (" in line:
            booted = True
            break
        if time.time() > deadline or server.poll() is not None:
            break
    if not booted:
        print("[protocol] NeoForge server failed to boot — see protocol-server.log", flush=True)
        server.kill()
        return 2

    print("[protocol] NeoForge server up — launching protocol client", flush=True)
    env = dict(os.environ, PROTO_PORT=PORT)
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
