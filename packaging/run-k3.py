#!/usr/bin/env python3
"""K3 orchestrator: boots the Fabric server, drives console commands in sync
with the bot scenario (permission grants, mute), and reports the verdict."""
import os
import re
import selectors
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SERVER_DIR = ROOT / "server"
JAVA = "/usr/lib/jvm/java-21-temurin/bin/java"

GRANTS = [
    "tp Alice 8 -60 8",
    "tp Bob 9 -60 9",
    "rock perms grant Alice rock.claims.*",
    "rock perms grant Alice rock.essentials.*",
    "rock perms grant Alice rock.economy.*",
    "rock perms grant Bob rock.essentials.*",
]


def main() -> int:
    log = open(SERVER_DIR / "k3-server.log", "w")
    server = subprocess.Popen(
        [JAVA, "-Xmx3g", "-jar", "fabric-server.jar", "nogui"],
        cwd=SERVER_DIR, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True, bufsize=1)

    def console(cmd: str) -> None:
        print(f"[console] {cmd}", flush=True)
        server.stdin.write(cmd + "\n")
        server.stdin.flush()

    # Wait for full boot (vanilla "Done (…s)!" line) while teeing the log.
    booted = False
    deadline = time.time() + 300
    for line in server.stdout:
        log.write(line)
        log.flush()
        if "Done (" in line:
            booted = True
            break
        if "ROCK platform ready" in line:
            print(f"[server] {line.strip()}", flush=True)
        if time.time() > deadline or server.poll() is not None:
            break
    if not booted:
        print("[K3] server failed to boot — see k3-server.log", flush=True)
        server.kill()
        return 2

    print("[K3] server is up — launching bots", flush=True)
    bots = subprocess.Popen(
        ["node", "k3-test.js"], cwd=ROOT / "bots",
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)

    # Pump both processes; react to bot wait-markers with console commands.
    sel = selectors.DefaultSelector()
    os.set_blocking(server.stdout.fileno(), False)
    os.set_blocking(bots.stdout.fileno(), False)
    sel.register(server.stdout, selectors.EVENT_READ, "server")
    sel.register(bots.stdout, selectors.EVENT_READ, "bots")

    deadline = time.time() + 420
    while time.time() < deadline and bots.poll() is None:
        for key, _ in sel.select(timeout=1):
            for line in iter(key.fileobj.readline, ""):
                if not line:
                    break
                if key.data == "server":
                    log.write(line)
                    log.flush()
                    if re.search(r"ROCK|rock_suite|ERROR|WARN.*rock", line):
                        print(f"[server] {line.rstrip()}", flush=True)
                else:
                    print(f"[bots] {line.rstrip()}", flush=True)
                    if "waiting for console grants" in line:
                        for grant in GRANTS:
                            console(grant)
                    if "waiting for console tp" in line:
                        console("tp Bob 9 -60 9")
                    if "waiting for console mute" in line:
                        console("rock mute Bob 1h k3-spam-test")

    bot_rc = bots.wait(timeout=30) if bots.poll() is None else bots.returncode
    console("stop")
    try:
        server.wait(timeout=60)
    except subprocess.TimeoutExpired:
        server.kill()
    log.close()
    print(f"[K3] bots exit code: {bot_rc}", flush=True)
    return bot_rc if bot_rc is not None else 3


if __name__ == "__main__":
    sys.exit(main())
