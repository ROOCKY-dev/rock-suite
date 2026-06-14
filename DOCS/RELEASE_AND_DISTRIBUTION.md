# ROCK SUITE — Release, Distribution & Contribution Strategy

How ROCK is versioned, built, published, supported, and contributed to. This is
the public-facing operating contract: what a server admin downloads, what a
developer depends on, what a contributor signs up to, and what each git tag
does.

> Status: design (v2.0.0-insiders). Items marked **[decision]** are the Project
> Owner's calls — current recommendation given; adjust as desired.

---

## 1. Principles

1. **Modular by default.** ROCK ships as independent jars; an admin installs the
   platform + adapter + *whichever modules they want*. No monolith, no fat jar.
2. **Cross-loader, one platform.** The same platform code runs on Fabric and
   NeoForge (Paper later); only the small adapter is loader/version specific.
3. **Server-authoritative, optional client.** Everything works with a vanilla
   client via commands; rock-client only adds GUIs.
4. **Boring releases.** Every release is the same automated, tag-driven pipeline
   with the same gates. Surprises live in CI, never in production.
5. **Apache-2.0, open.** Free and open source; contributions welcome under DCO.

---

## 2. Versioning — two tracks

ROCK runs **two parallel version namespaces** for the *same* codebase:

| Track | Audience | Numbering | Channels |
|---|---|---|---|
| **Insider** | testers, contributors, early adopters | continues `v2.0.0 → v2.1.0 → …` | GitHub pre-release + GitHub Packages |
| **Public** | server admins | **restarts at `v1.0.0-beta` → `v1.0.0` → `v1.1.0`** | Modrinth + CurseForge + GitHub Release (+ Maven Central) |

- The whole journey so far (insider `v1.0.0 → v2.0.0`) was the **insider** track.
- The **public** track begins now: today's insider `v2.0.0` == public
  `v1.0.0-beta` (the first thing the public ever sees).
- **Mapping rule:** `public major = insider major − 1` → insider `2.x` ↔ public
  `1.x`. A promoted build carries both numbers; the mapping is recorded in
  `CHANGELOG.md` and the release notes.
- Both tracks are **semver**: `MAJOR` = breaking platform/API change,
  `MINOR` = new modules/features (backwards-compatible), `PATCH` = fixes.
- `rock-api` is the compatibility anchor: a `MAJOR` bump means an `rock-api`
  breaking change (modules built against the old major must be recompiled).

---

## 3. Release channels (the "plans")

Three promotion stages, each a Modrinth/CurseForge **release type** and a GitHub
release state:

| Channel | Who | Stability | Storefront type | GitHub | Maven Central |
|---|---|---|---|---|---|
| **Insider** | contributors/testers | bleeding edge, may break | — (not on storefronts) | pre-release + Packages | no |
| **Beta** | brave admins | feature-complete, hardening | `beta` | pre-release + Packages | no |
| **Stable** | everyone | production | `release` | release + Packages | yes |

Flow: a change lands on `main` (insider) → soaks → promoted to **Beta** on the
public track → after the soak window, promoted to **Stable**. LTS lines only
ever receive Beta→Stable bugfix/security promotions, never new features.

---

## 4. Git tag system (the automation contract)

Everything is **tag-driven** (`.github/workflows/release.yml`). One push of a
tag does the whole release. Tag grammar:

```
v<version>[-<prerelease>]          e.g. v1.0.0  ·  v1.0.0-beta.2  ·  v2.1.0-insiders.1
```

| Tag pattern | Channel | Publishes to |
|---|---|---|
| `v1.2.3` (no suffix) | **Stable** | Maven Central + GitHub Release + Modrinth/CF `release` |
| `v1.2.3-beta.N` | **Beta** | GitHub pre-release + Packages + Modrinth/CF `beta` |
| `v1.2.3-insiders.N` | **Insider** | GitHub pre-release + Packages only |

Rules baked into the workflow:
- A hyphen in the tag ⇒ pre-release ⇒ **never** Maven Central (insider/beta stay
  off the public Java index). *(Already implemented.)*
- The Maven Central step self-skips unless `OSSRH_*` secrets exist.
- GitHub Packages is idempotent (409-tolerant) so re-running a tag is safe.
- **[to add]** A storefront step (Modrinth/CurseForge) gated on tag type, using
  `modrinth-publish`/`cf` actions; reads tokens from CI secrets (never the repo).

Artifact naming carries the MC target as build metadata so one release can hold
the whole matrix:
```
rock-loader-fabric-1.0.0+mc1.21.11.jar
rock-loader-neoforge-1.0.0+mc1.21.11.jar
rock-core-1.0.0.jar            (platform/modules are MC-agnostic — no +mc)
```

### Every public GitHub Release ships loader-READY bundles

Server admins must never be handed bare Maven jars (no `fabric.mod.json` ⇒
Fabric silently ignores them — a real failure mode we hit). So each public
release attaches **ready-to-drop bundles, one per loader × supported MC**, built
by `packaging/` (modwrap + the adapter), as the primary, clearly-named assets:

```
rock-suite-fabric-<mcver>-<ver>.zip     ← extract into mods/  (the install)
rock-suite-neoforge-<mcver>-<ver>.zip
```
Each bundle contains the wrapped library mods (with descriptors), the loader
adapter, and a README listing the required loader API (Fabric API / NeoForge).
The **raw `dev.rock:*` jars are NOT release assets** — they live on Maven
Central / GitHub Packages for *developers only*. **[pipeline TODO]** extend
`release.yml` to run the packaging step and attach these bundles (today they're
attached manually).

---

## 5. Supported Minecraft matrix & LTS policy

ROCK publishes **per loader × per supported MC version**. The platform jars are
MC-agnostic (one build); only the loader adapter is per-version.

| MC family | Published target | Loaders | Tier | Support window |
|---|---|---|---|---|
| **26.x** (year-based) | latest `26.x` | Fabric, NeoForge | **Current** | until next Current |
| **1.21.x** | latest `1.21.x` | Fabric, NeoForge | **LTS** | ≥ 12 months, ≥ 6 months past next LTS |
| **1.20.x** | `1.20.1` | Fabric, NeoForge | **Legacy LTS** | security/critical only |

**[decision] LTS picks:** `1.21.x` (modern modded anchor) is the primary LTS;
`1.20.1` is the legacy LTS (massive existing modpack population); `26.x` is
Current (newest features land here first, promoted to LTS once it matures).

Definitions:
- **Current** — newest MC; gets new features first.
- **LTS** — committed long-term support: every Stable patch is backported;
  supported well past the next MC drop so admins aren't forced to upgrade.
- **Legacy LTS** — security + critical bugfixes only; no new features.
- **Best-effort / EOL** — older versions: community PRs welcome, no guarantees.

A version's support state is published in `SUPPORTED_VERSIONS.md` and on each
storefront page. Paper is a **Phase 2** loader (`rock-loader-paper`) — same
matrix once it lands.

---

## 6. Distribution layout (where things live)

| Artifact | Home | For |
|---|---|---|
| **One project per module** (`rock-claims`, `rock-economy`, …) | **Modrinth** + **CurseForge** | server admins |
| **ROCK Core** (rock-api+core+data+protocol + loader adapter) | **Modrinth** + **CurseForge** project `rock-core` | the required base |
| **rock-client** | separate **Modrinth**/**CF** project `rock-client` | players (optional) |
| Loader-ready bundles (per MC×loader) | **GitHub Releases** (zip) — see §4 | admins who prefer GitHub |
| `dev.rock:*` libraries | **Maven Central** (stable) + **GitHub Packages** (all) | module/plugin developers |
| Source | **GitHub** `ROOCKY-dev/rock-suite` (monorepo) + `ROOCKY-dev/rock-client` | contributors |

**Storefront project model [decision — per Owner]: one project per module, with
linked dependencies — install only what you need.** Each feature module is its
own Modrinth/CurseForge page (`rock-claims`, `rock-economy`, `rock-web`, …). The
required foundation ships as a single **`ROCK Core`** project (rock-api + core +
data + protocol + the loader adapter). Every module page declares its
**dependencies** so the storefront resolves *exactly* what's needed:

- every module → **required:** `ROCK Core` + the loader's API (Fabric API / NeoForge)
- inter-module → **required:** e.g. `rock-claims` → `rock-permissions`;
  `rock-economy` → `rock-permissions`
- soft links → **optional:** e.g. `rock-web` ↔ `rock-protocol` features

So an admin who wants just claims installs **rock-claims**, and Modrinth/CF pull
in ROCK Core + rock-permissions automatically — nothing more. `rock-client` is
its own page. Each module version is uploaded per MC version, tagged with both
loaders. (Trade-off: ~13 pages to maintain — automated by the publish step in
§4, which pushes each module to its own project from one release.)

---

## 7. How the public uses ROCK

**Server admin (the common path):**
1. On the storefront, pick your **MC version + loader** (Fabric/NeoForge) and
   install the **module(s) you want** (e.g. `rock-claims`). The storefront pulls
   the linked dependencies automatically — **ROCK Core**, any required modules
   (e.g. `rock-permissions`), and the loader API (Fabric API). Install only what
   you need.
   - *Or, from GitHub:* download the loader-ready bundle (§4), extract into
     `mods/`, and delete any module jars you don't want.
2. Start on **Java 21**. Configure via `config/rock/*.toml`; secrets via env vars.
3. Optional: set `ROCK_WEB_ADMIN_PASSWORD` to enable the web dashboard.

**Player (optional):** install `rock-client` for the in-game map/HUD; a vanilla
client keeps 100% functionality via commands.

**Developer (build a module / integrate):**
```kotlin
repositories { mavenCentral() }                       // stable
dependencies { compileOnly("dev.rock:rock-api:1.0.0") } // contracts only
```
Implement `RockModule`, ship a jar, drop it in `mods/` — discovered via
ServiceLoader. Modules see **rock-api only** (the platform's stable contract).

---

## 8. Contribution model

Public entry point: **`CONTRIBUTING.md`** (root). Summary:

- **License & sign-off:** Apache-2.0; every commit needs a **DCO** `Signed-off-by`
  line (`git commit -s`). No CLA.
- **Where:** platform + modules in this monorepo; `rock-client` in its own repo
  (client toolchain). Adapters live only under `loaders/` + `packaging/`.
- **Branch model:** `main` is the insider/dev line. Stable/LTS fixes land on
  `main` then cherry-pick to `release/<public-version>` branches; releases are
  cut by tag. Contributors work on feature branches → PR to `main`.
- **The laws contributors MUST respect** (CI-enforced where possible):
  - `rock-api` has **zero non-JDK dependencies**.
  - Modules depend on **rock-api only** — never rock-core/data/each other
    (AVD Principle 1, REH §7).
  - Loader/Minecraft types appear **only** under `loaders/`+`packaging/`
    (AVD Rule Zero; `verifyLoaderIsolation` gate).
  - Constructor injection; cross-module access via `ServiceRegistry` at call
    time (DIS); all DB work async/off the tick thread.
- **CI gates (must be green to merge):** full build, all tests, the architecture
  checks, the testbench. Larger changes should add/extend tests; protocol/loader
  changes are validated by the on-server harness.
- **Module SDK:** third parties build their own `rock-*` modules against
  rock-api the same way first-party modules do — the extension point is the
  product, not an afterthought.
- **Issue/PR templates + a public roadmap board** ship in `.github/`.

---

## 9. Plans going forward (the train)

- **Public v1.0.0-beta → v1.0.0:** harden on the insider feedback + the Modrinth
  test pass; first public Stable once the beta soak is clean.
- **Insider v2.x:** features land here first (new modules, rock-client Tier 2,
  protocol growth), promoted down to public after soaking.
- **Version matrix growth:** add the `26.x` adapter; keep `1.21.x` LTS current;
  maintain `1.20.1` legacy LTS.
- **Loader expansion:** `rock-loader-paper` (Phase 2) brings the Vault bridge.
- **rock-client:** ship Tier 1 publicly, then Tier 2 (wallet HUD, permission
  editor) on the insider track.

---

## 10. Release checklist (per release)

1. `main` green (build + tests + architecture + testbench).
2. `CHANGELOG.md` updated; insider↔public version mapping noted.
3. On-server harness pass for any loader/protocol change (per supported MC).
4. Bump versions; tag per §4 (`-insiders.N` / `-beta.N` / bare for Stable).
5. Push tag → pipeline publishes to the channel's targets.
6. Verify: GitHub Release assets, Packages/Maven Central, storefront versions.
7. Update `SUPPORTED_VERSIONS.md` if the matrix/tiers changed.
