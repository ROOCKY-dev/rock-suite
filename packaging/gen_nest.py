#!/usr/bin/env python3
"""Regenerate /tmp/rock-nest.json for the Fabric modwrap.

Third-party libraries ride nested inside the one ROCK jar that owns them
(rock-core, rock-data). This resolves each owner's runtime classpath, keeps the
external (non-ROCK) jars, and assigns each lib to exactly one owner (core wins
ties so a lib is never nested twice). Output matches what modwrap.py expects:

    { "rock-core": [{"name","version","jar"}, ...], "rock-data": [...] }
"""
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OWNERS = ["rock-core", "rock-data", "rock-web"]

NAME_VER = re.compile(r"^(.*?)-(\d[\w.\-]*)\.jar$")


def parse(jarname: str):
    m = NAME_VER.match(jarname)
    if not m:
        return jarname[:-4], "0"
    return m.group(1), m.group(2)


def runtime_externals(project: str) -> list[Path]:
    out = subprocess.run(
        ["./gradlew", "-I", "/tmp/dump-rt.gradle.kts", f":{project}:dumpRt", "-q", "--console=plain"],
        cwd=ROOT, capture_output=True, text=True)
    jars = []
    for line in out.stdout.splitlines():
        if line.startswith("RTJAR\t"):
            p = Path(line.split("\t", 1)[1])
            if "/Projects/ROCK/" not in str(p) and p.suffix == ".jar":
                jars.append(p)
    return jars


def main() -> None:
    nest: dict[str, list] = {}
    claimed: set[str] = set()
    for owner in OWNERS:
        entries = []
        for jar in sorted(runtime_externals(owner)):
            name, version = parse(jar.name)
            if name in claimed:
                continue  # already nested in an earlier owner
            claimed.add(name)
            entries.append({"name": name, "version": version, "jar": str(jar)})
        nest[owner] = entries
        print(f"{owner}: {len(entries)} nested libs")
    Path("/tmp/rock-nest.json").write_text(json.dumps(nest, indent=1))
    print("wrote /tmp/rock-nest.json")


if __name__ == "__main__":
    if not Path("/tmp/dump-rt.gradle.kts").exists():
        sys.exit("missing /tmp/dump-rt.gradle.kts (the runtimeClasspath dump init script)")
    main()
