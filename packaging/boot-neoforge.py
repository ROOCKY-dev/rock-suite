#!/usr/bin/env python3
"""K4 smoke boot: start the NeoForge server, watch for ROCK startup / errors,
optionally run console /rock checks, then stop cleanly. Prints a verdict."""
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SERVER = ROOT / "server-neoforge"
JAVA = "/usr/lib/jvm/java-21-temurin/bin/java"

# Read the NeoForge-generated arg files (classpath + module setup).
ARGS = (SERVER / "libraries/net/neoforged/neoforge/21.11.42/unix_args.txt")


def main() -> int:
    log = open(SERVER / "k4-server.log", "w")
    proc = subprocess.Popen(
        [JAVA, "@user_jvm_args.txt", f"@{ARGS}", "nogui"],
        cwd=SERVER, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True, bufsize=1)

    def console(cmd):
        print(f"[console] {cmd}", flush=True)
        proc.stdin.write(cmd + "\n")
        proc.stdin.flush()

    rock_started = False
    booted = False
    checks_sent = False
    errors = []
    deadline = time.time() + 300
    for line in proc.stdout:
        log.write(line)
        log.flush()
        s = line.rstrip()
        if "ROCK SUITE started on NeoForge" in s and not checks_sent:
            rock_started = True
            print(f"[rock] {s}", flush=True)
            # Platform is fully enabled now — exercise /rock through the real
            # command tree with a DB-writing path we can verify afterwards.
            console("rock version")
            console("rock perms group create Knights 50")
            console("rock perms group grant Knights rock.claims.create")
            checks_sent = True
            time.sleep(4)
            console("stop")
        if re.search(r"rock|ROCK", s) and re.search(r"ERROR|Exception|Caused by", s):
            errors.append(s)
            print(f"[err] {s}", flush=True)
        if re.search(r"Failed to load|ModLoadingException|module .* reads package|constructing", s):
            print(f"[load] {s}", flush=True)
        if "Done (" in s:
            booted = True
            print(f"[server] {s}", flush=True)
        if checks_sent and re.search(r"\[ROCK\]|version|granted|Unknown command", s, re.I):
            print(f"[check] {s}", flush=True)
        if time.time() > deadline or proc.poll() is not None:
            break

    try:
        proc.wait(timeout=60)
    except subprocess.TimeoutExpired:
        proc.kill()
    log.close()

    print("\n===== K4 VERDICT =====", flush=True)
    print(f"server reached 'Done': {booted}", flush=True)
    print(f"ROCK platform started:  {rock_started}", flush=True)
    print(f"rock-tagged errors:     {len(errors)}", flush=True)
    return 0 if (booted and rock_started and not errors) else 1


if __name__ == "__main__":
    sys.exit(main())
