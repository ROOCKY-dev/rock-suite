#!/usr/bin/env python3
"""K4 packaging: lay the ROCK platform into a NeoForge server's mods/ folder.

NeoForge only loads a jar from mods/ if it is a mod (carries
META-INF/neoforge.mods.toml) or declares itself a library via the manifest
attribute `FMLModType: GAMELIBRARY`. So, mirroring the Fabric modwrap:

  * the real adapter jar is the single MOD (it already has neoforge.mods.toml);
  * every ROCK platform/feature jar and every third-party runtime jar is
    stamped GAMELIBRARY so NeoForge puts it on the game classpath, where the
    modules are then discovered by ServiceLoader exactly as in the testbench.

Unlike Fabric (nested jars), NeoForge resolves the module path flat, so the
libraries sit beside the mods rather than nested inside rock-core/rock-data —
still the modular install (every ROCK piece a separate, removable jar), no
monolith and no fat jar.

Libraries the NeoForge/Minecraft runtime already provides are skipped: nesting
our older Guava over Minecraft's newer copy crashes vanilla code paths, and a
second slf4j binding would clash with NeoForge's logging.
"""
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RT_LIST = Path("/tmp/rt-jars.txt")
ADAPTER = ROOT / "packaging" / "neoforge-mod" / "build" / "libs" / "rock-neoforge-mod-2.0.0.jar"
MODS = ROOT / "packaging" / "server-neoforge" / "mods"

# Provided by NeoForge/Minecraft — bundling our copy shadows or clashes.
PROVIDED = {
    "guava", "failureaccess",
    "listenablefuture",  # filename is "listenablefuture-9999.0-empty..."
    "jsr305", "error_prone_annotations", "j2objc-annotations", "checker-qual",
    "slf4j-api", "slf4j-simple",
}


def is_provided(filename: str) -> bool:
    """Match a library by name prefix: guava-31.0.1-jre.jar -> guava."""
    return any(filename == p + ".jar" or filename.startswith(p + "-") for p in PROVIDED)


def add_fml_modtype(manifest: str) -> str:
    """Insert `FMLModType: GAMELIBRARY` into the manifest's MAIN section.

    The main section ends at the first blank line; anything after it lives in a
    per-entry section and is ignored by the loader — which is exactly the bug
    that made a naive append silently fail.
    """
    lines = manifest.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    try:
        blank = lines.index("")  # first blank line terminates the main section
    except ValueError:
        blank = len(lines)
    main, rest = lines[:blank], lines[blank:]
    if not main:
        main = ["Manifest-Version: 1.0"]
    if not any(l.startswith("FMLModType") for l in main):
        main.append("FMLModType: GAMELIBRARY")
    out = "\r\n".join(main + rest)
    if not out.endswith("\r\n"):
        out += "\r\n"
    return out


def stamp_gamelibrary(src: Path, dest: Path) -> None:
    """Copy a plain library jar, adding FMLModType: GAMELIBRARY to its manifest."""
    with zipfile.ZipFile(src) as zin:
        names = zin.namelist()
        manifest = ""
        if "META-INF/MANIFEST.MF" in names:
            manifest = zin.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")
        new_manifest = add_fml_modtype(manifest)
        with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as zout:
            zout.writestr("META-INF/MANIFEST.MF", new_manifest)
            for item in zin.infolist():
                if item.filename == "META-INF/MANIFEST.MF":
                    continue
                zout.writestr(item, zin.read(item.filename))


def main() -> None:
    if MODS.exists():
        shutil.rmtree(MODS)
    MODS.mkdir(parents=True)

    jars = [Path(line.strip()) for line in RT_LIST.read_text().splitlines() if line.strip()]
    wrapped = skipped = 0
    for jar in jars:
        if is_provided(jar.name):
            print(f"skip   {jar.name}  (provided by NeoForge/MC)")
            skipped += 1
            continue
        stamp_gamelibrary(jar, MODS / jar.name)
        wrapped += 1
        print(f"lib    {jar.name}  -> GAMELIBRARY")

    # The adapter is the one real mod (keeps its neoforge.mods.toml untouched).
    shutil.copy(ADAPTER, MODS / ADAPTER.name)
    print(f"MOD    {ADAPTER.name}  (real NeoForge adapter)")
    print(f"\n{wrapped} libraries stamped, {skipped} skipped, 1 adapter mod -> {MODS}")


if __name__ == "__main__":
    if not ADAPTER.is_file():
        sys.exit(f"missing adapter jar: {ADAPTER}")
    main()
