# Contributing to ROCK SUITE

Thanks for helping build ROCK — the cross-platform Minecraft server management
platform. This is the quick start; the full release/distribution model is in
[`DOCS/RELEASE_AND_DISTRIBUTION.md`](DOCS/RELEASE_AND_DISTRIBUTION.md), and the
architecture is in `DOCS/` (CHARTER, AVD, REH, DIS, TRS).

## Where things live

| You want to work on… | Repo / path |
|---|---|
| Platform (api/core/data/protocol) | this repo, `platform/` |
| A feature module | this repo, `modules/rock-*` |
| A loader adapter | this repo, `loaders/` + `packaging/` only |
| The in-game client | separate repo `ROOCKY-dev/rock-client` |

## Ground rules (these are enforced — CI will fail otherwise)

1. **`rock-api` has zero non-JDK dependencies.** It is pure contracts.
2. **Modules see `rock-api` only** — never rock-core, rock-data, or each other
   (AVD Principle 1 / REH §7). Reach sibling services via `ServiceRegistry` at
   call time, never by constructor-injecting another module (DIS).
3. **Minecraft / loader types live only under `loaders/` and `packaging/`**
   (AVD Rule Zero). Everything inward is plain Java 21. The
   `verifyLoaderIsolation` gate enforces this.
4. **Constructor injection** (Guice); **all DB work is async / off the tick
   thread**; **secrets via env vars, never committed.**

## Workflow

1. Fork & branch off `main` (the insider/dev line).
2. Build & test locally: `./gradlew build` (full suite + architecture checks +
   testbench). Java 21 required.
3. Add or extend tests for your change. Loader/protocol changes should pass the
   on-server harness (`packaging/`).
4. Commit with **DCO sign-off**: `git commit -s` (adds `Signed-off-by:`). By
   signing off you certify the [DCO](https://developercertificate.org/). No CLA.
5. Open a PR to `main`. Keep it focused; describe the why. Green CI is required.

## Building a module (the extension point)

ROCK is meant to be extended. A third-party module is just a jar that implements
`dev.rock.api.module.RockModule`, depends on **rock-api only**, and is dropped in
`mods/` (discovered via ServiceLoader):

```kotlin
repositories { mavenCentral() }
dependencies { compileOnly("dev.rock:rock-api:<version>") }
```

## Releases

Tag-driven and automated — see `DOCS/RELEASE_AND_DISTRIBUTION.md`. In short:
`vX.Y.Z` = public Stable (Modrinth/CurseForge/Maven Central), `-beta.N` = public
Beta, `-insiders.N` = insider pre-release. Contributors don't cut releases; the
maintainers do, from `main`.

## Reporting bugs / ideas

Use the issue templates. For a server bug, include MC version + loader, the ROCK
versions (`/rock version` + `/rock modules`), and the relevant server log.
