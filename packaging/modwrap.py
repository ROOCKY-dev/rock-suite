#!/usr/bin/env python3
"""K3 packaging: wraps plain ROCK jars as Fabric library mods.

Fabric only loads jars from mods/ that carry a fabric.mod.json, so every ROCK
jar gets a descriptor (keeping the modular install flow: admins pick any
subset of modules). Third-party libraries ride nested inside the one ROCK jar
that owns them (rock-core, rock-data) — nested jars must themselves be mods,
so each lib gets a generated descriptor too, exactly as Loom's include() does.
"""
import json
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "packaging" / "dist-mods"
VERSION = "1.7.0"

# id → (jar dir name, fabric depends)
ROCK_JARS = {
    "rock-api": ("platform/rock-api", {}),
    "rock-core": ("platform/rock-core", {"rock-api": "*"}),
    "rock-data": ("platform/rock-data", {"rock-api": "*", "rock-core": "*"}),
    "rock-protocol": ("platform/rock-protocol", {"rock-api": "*", "rock-core": "*"}),
    "rock-permissions": ("modules/rock-permissions", {"rock-core": "*", "rock-data": "*"}),
    "rock-claims": ("modules/rock-claims", {"rock-core": "*", "rock-data": "*", "rock-permissions": "*"}),
    "rock-economy": ("modules/rock-economy", {"rock-core": "*", "rock-data": "*", "rock-permissions": "*"}),
    "rock-discord": ("modules/rock-discord", {"rock-core": "*", "rock-data": "*", "rock-permissions": "*"}),
    "rock-logging": ("modules/rock-logging", {"rock-core": "*", "rock-data": "*"}),
    "rock-teams": ("modules/rock-teams", {"rock-core": "*", "rock-data": "*"}),
    "rock-essentials": ("modules/rock-essentials", {"rock-core": "*", "rock-data": "*", "rock-permissions": "*"}),
    "rock-moderation": ("modules/rock-moderation", {"rock-core": "*", "rock-data": "*", "rock-permissions": "*"}),
    "rock-backup": ("modules/rock-backup", {"rock-core": "*", "rock-data": "*"}),
    "rock-metrics": ("modules/rock-metrics", {"rock-core": "*"}),
    "rock-migrate": ("modules/rock-migrate", {"rock-core": "*", "rock-data": "*", "rock-permissions": "*"}),
    "rock-web": ("modules/rock-web", {"rock-core": "*", "rock-data": "*"}),
}


# Libraries the vanilla server (or fabric loader) already provides — nesting
# them shadows Minecraft's newer copies and crashes vanilla code paths
# (observed: our guava 31 vs Minecraft's 32+ → NoSuchMethodError at boot).
# Compile-time annotation jars are dropped too; nothing needs them at runtime.
PROVIDED_OR_USELESS = {
    "guava", "failureaccess", "listenablefuture",
    "jsr305", "error_prone_annotations", "j2objc-annotations", "checker-qual",
}


def lib_id(name: str) -> str:
    sanitized = name.lower().replace(".", "-").replace("_", "-")
    return sanitized if sanitized[0].isalpha() else "lib-" + sanitized


def lib_fmj(name: str, version: str) -> bytes:
    return json.dumps({
        "schemaVersion": 1,
        "id": lib_id(name),
        "version": version,
        "name": name + " (bundled by ROCK)",
        "environment": "*",
    }).encode()


def wrap_lib(src: Path, name: str, version: str, dest: Path) -> None:
    shutil.copy(src, dest)
    with zipfile.ZipFile(dest, "a") as zf:
        if "fabric.mod.json" not in zf.namelist():
            zf.writestr("fabric.mod.json", lib_fmj(name, version))


def wrap_rock(jar: Path, mod_id: str, depends: dict, nested: list, dest: Path, tmp: Path) -> None:
    shutil.copy(jar, dest)
    nested_entries = []
    with zipfile.ZipFile(dest, "a") as zf:
        for dep in nested:
            if not dep["jar"] or dep["name"] in PROVIDED_OR_USELESS:
                continue
            wrapped = tmp / f'{dep["name"]}-{dep["version"]}.jar'
            wrap_lib(Path(dep["jar"]), dep["name"], dep["version"], wrapped)
            arc = f'META-INF/jars/{wrapped.name}'
            zf.write(wrapped, arc)
            nested_entries.append({"file": arc})
        fmj = {
            "schemaVersion": 1,
            "id": mod_id,
            "version": VERSION,
            "name": "ROCK " + mod_id.removeprefix("rock-").capitalize(),
            "description": "ROCK SUITE component (library mod — no entrypoints).",
            "license": "Apache-2.0",
            "environment": "*",
            "depends": {"fabricloader": ">=0.16.0", **depends},
        }
        if nested_entries:
            fmj["jars"] = nested_entries
        zf.writestr("fabric.mod.json", json.dumps(fmj, indent=1))


def main() -> None:
    nest = json.load(open("/tmp/rock-nest.json"))
    OUT.mkdir(parents=True, exist_ok=True)
    tmp = OUT / ".libs"
    tmp.mkdir(exist_ok=True)
    for mod_id, (project_dir, depends) in ROCK_JARS.items():
        name = project_dir.split("/")[-1]
        jar = ROOT / project_dir / "build" / "libs" / f"{name}-{VERSION}.jar"
        if not jar.is_file():
            sys.exit(f"missing build output: {jar}")
        wrap_rock(jar, mod_id, depends, nest.get(name, []), OUT / jar.name, tmp)
        print(f"wrapped {jar.name}  (+{len(nest.get(name, []))} nested libs)")
    shutil.rmtree(tmp)


if __name__ == "__main__":
    main()
