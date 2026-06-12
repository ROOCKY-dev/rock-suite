# ROCK SUITE — Packaging & On-Server Testing (K3)

This directory turns the platform's plain JARs into installable Fabric mods and
runs the suite on a **real Minecraft 1.21.11 dedicated server** with two real
network clients. It is the K3 milestone from MODULE_ROADMAP: the platform is no
longer only proven in the JVM testbench — it boots and behaves correctly on
actual Minecraft.

## The modular install (no monolith, no fat jar)

ROCK ships as independent JARs; an admin drops `rock-api`, `rock-core`,
`rock-data`, the Fabric adapter, and **whichever feature modules they want**
into `mods/`. Any subset works — that is the whole point of the architecture.

`modwrap.py` adds a `fabric.mod.json` to each ROCK JAR (Fabric ignores
descriptor-less JARs in `mods/`) with correct inter-module `depends`, so admins
get clean dependency errors instead of crashes. Third-party libraries that will
never carry a mod descriptor (Guice, tomlj, HikariCP, JDBI, Flyway, SQLite…)
ride **nested inside the one ROCK JAR that owns them** — rock-core and
rock-data — exactly as Loom's `include()` does. This is library bundling, not
a monolith: every ROCK piece stays a separate, removable JAR.

> Note: libraries the server already provides (Guava and its annotation
> constellation) are **not** nested — shadowing Minecraft's newer Guava with
> our older copy crashes vanilla code paths at boot (`buildKeepingLast`
> NoSuchMethodError). Guice runs fine on Minecraft's Guava.

## The real adapter (`fabric-mod/`)

A Loom project compiling the genuine entrypoint against Mojang-mapped
Minecraft. It is built separately from the hermetic monorepo (which compiles
the loader adapters against stubs) and consumes ROCK from `mavenLocal`. It adds
what the stub adapter never could: brigadier registration of the `/rock`
command tree plus the short aliases (`/r`, `/sethome`, `/home`, `/pay`,
`/balance`, …) and the real ban/chat/block-break event mappings.

## Running the on-server test

```bash
# 1. Publish ROCK to mavenLocal and build the module JARs
./gradlew publishToMavenLocal build           # from repo root

# 2. Wrap JARs as Fabric mods (reads /tmp/rock-nest.json — see below)
python3 packaging/modwrap.py

# 3. Build the real Loom adapter (needs a Java 21 Gradle JVM)
cd packaging/fabric-mod && gradle build

# 4. Install all mods + the adapter, then orchestrate server + bots
cp packaging/dist-mods/*.jar packaging/fabric-mod/build/libs/*.jar packaging/server/mods/
cd packaging && python3 run-k3.py
```

`run-k3.py` boots the server, waits for `ROCK platform ready`, launches the
mineflayer bots (`bots/k3-test.js`), and drives console commands (permission
grants, mute) in sync with the scenario.

## What the bots verify (13 checks, all green)

`/rock version` over the wire · `/r` alias routing · permission gating before
grants · Alice claims her chunk · **Bob's grief break cancelled** (judged by
what the server broadcasts to Alice's client) · Alice builds in her own claim ·
trust Bob → Bob can build · chat delivery · **mute silences Bob** · `/sethome`
+ `/homes` · `/balance` — all through the real protocol.

The server database (`server/rock/rock.db`) is the receipt: 2 players, Alice's
claim, Bob's BUILD membership, exactly the 2 *approved* breaks in the world log
(the pre-trust grief is correctly absent), the MUTE punishment + its audit
entry, and Alice's home — with zero ROCK errors in the server log.
