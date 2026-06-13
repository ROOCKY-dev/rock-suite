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

---

# K4 — the same platform on real NeoForge

K4 repeats K3 on the **other** Tier-1 loader: a real NeoForge **21.11.42**
dedicated server (Minecraft 1.21.11 — exact parity with the K3 Fabric server).
The entire point is that **nothing below the loader seam changed.** The real
NeoForge adapter (`neoforge-mod/`, a standalone ModDevGradle build) compiled
against genuine NeoForge + Mojang-mapped Minecraft on the first try with zero
API fixups: the same `world.dimension().identifier()`, `BuiltInRegistries.BLOCK`
and `source.permissions().hasPermission(COMMANDS_ADMIN)` calls the Fabric
adapter uses port verbatim. The platform jars are byte-identical to Fabric's.

## NeoForge's modular install (`modwrap_neoforge.py`)

NeoForge only loads a `mods/` jar that is a mod (carries `neoforge.mods.toml`)
or declares itself a library via the manifest attribute
`FMLModType: GAMELIBRARY`. So, mirroring the Fabric wrap: the adapter jar is the
single **mod**, and every ROCK platform/feature jar plus every third-party
runtime jar is stamped `GAMELIBRARY` (into the manifest **main** section — an
attribute after the first blank line lands in a per-entry section and is
silently ignored). NeoForge then places them on the game classpath, where the
modules are discovered by ServiceLoader exactly as in the testbench. Libraries
the runtime already provides (Guava + its annotation constellation, slf4j) are
skipped — the same shadowing hazard documented for Fabric. Unlike Fabric's
nested jars, NeoForge resolves the module path flat, so libraries sit beside the
mods rather than nested — still every ROCK piece a separate, removable jar.

## One platform fix this surfaced (loader portability)

Flyway's classpath **scanner** cannot enumerate resources inside a
module-isolating classloader, so on NeoForge it found zero migrations and every
module failed its first query. `DataMigrator` now reads a committed
`db/migration/index.txt` (a single resource, always loadable by name via
`getResourceAsStream` on any classloader — the same trick
`MigrationRollbackRunner` already used for undo scripts), extracts the listed
scripts to a temp dir, and points Flyway at its rock-solid **filesystem**
scanner. SQL content is byte-identical, so recorded checksums stay portable
across loaders. This is the one change K4 needed, and it is a genuine platform
hardening, not a NeoForge workaround.

## Running the on-server test

```bash
./gradlew publishToMavenLocal -x test                 # ROCK -> mavenLocal at current version
cd packaging/neoforge-mod && gradle jar               # build the real adapter (Java 21 JVM)
cd .. && curl -O .../neoforge-21.11.42-installer.jar  # + java -jar … --installServer  (one-time)
python3 modwrap_neoforge.py                            # stamp + lay all jars into server-neoforge/mods/
python3 boot-neoforge.py                               # boot, run /rock checks, print verdict
```

## K4 verdict (clean)

Real NeoForge boots → the adapter `@Mod` (`rock_suite`) loads → the ROCK
platform boots and **all 12 feature modules enable** via ServiceLoader → the
full 22-table schema migrates (14/14) → `/rock perms group create Knights 50`
and `… group grant Knights rock.claims.create` execute through the real
brigadier tree and **persist to the world DB** — with zero ROCK errors (only the
expected "no discord.token configured" no-op WARN). The cross-loader thesis is
now proven empirically on both Tier-1 loaders.

---

# K5 — rock-protocol on the wire (real client check, both loaders)

K3/K4 proved the **server**; K5 proves the **client seam**. The projection layer
(`platform/rock-protocol`) is wired to a real transport — the `rock:protocol`
custom-payload channel — and exercised end-to-end by a protocol-aware client
against both real servers.

## The transport per loader

Each real adapter carries the only loader-networking-aware code; the wire model
(`ProtocolMessage`/`ProtocolCodec`) and the hub stay identical:

* **Fabric** (`fabric-mod`): `PayloadTypeRegistry` (C2S+S2C) + a global receiver
  routing inbound frames into `ProtocolHub.receive`; outbound via
  `ServerPlayNetworking.send`.
* **NeoForge** (`neoforge-mod`): `RegisterPayloadHandlersEvent` →
  `registrar("1").optional().playBidirectional(...)`; outbound via
  `PacketDistributor.sendToPlayer`. `optional()` lets non-NeoForge clients
  connect.

Both adapters add `RockProtocolModule` to the boot (the hub auto-enables as a
PRODUCTION-stage Guice singleton) and register the transport in the
`ServiceRegistry`. The `RockProtocolPayload` is byte-identical across loaders:
the body is the raw `ProtocolCodec` frame as the rest of the packet buffer (no
inner length prefix — vanilla framing).

## The real client check

`bots/protocol-bot.js` is a protocol-aware mineflayer client speaking the exact
`ProtocolCodec` layout. `run-protocol.py` (Fabric) and `run-protocol-neoforge.py`
(NeoForge) boot the server, grant `rock.client.*` to the bot on its
`AWAITING_GRANTS` marker, and report the verdict.

**6/6 green on both loaders:** Welcome over `rock:protocol` · protocol version
negotiated · **permission-gated** capabilities granted (`CLAIMS`,`WALLET` — only
after the console grant) · `session.ping`→`session.pong` (nonce echoed) ·
`claims.list`→`claim.list.end` (inbound intent → `ClaimService` → outbound
projection). Zero ROCK errors. A modified client gains nothing — every
capability and intent is re-validated server-side.
