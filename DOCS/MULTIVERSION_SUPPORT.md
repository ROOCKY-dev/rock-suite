# ROCK SUITE — Multi-Version Minecraft Support (Exploration)

**Status:** Plan — NOT scheduled yet. Captured at Project Owner's request to
guide a later milestone. No platform code changes are implied today.

**Target matrix (eventual):** 1.20.x · 1.21.x · 26.x (Mojang's new
year-based numbering, e.g. 26.1, 26.2) — across Fabric and NeoForge.

---

## 1. The good news: the platform is already version-agnostic

ROCK's layering was built so that *all* version- and loader-specific code lives
in one place — the loader adapter. Concretely:

| Layer | Touches Minecraft? | Version-coupled? |
|---|---|---|
| rock-api, rock-core, rock-data | No (pure Java 21 + libs) | **No** |
| modules (claims, economy, …) | No (rock-api only) | **No** |
| loaders/ stub adapters | No (compile against stubs) | **No** |
| **packaging/fabric-mod (real adapter)** | **Yes** | **Yes — the only place** |

The K3 work proved this empirically: moving from no-server to MC 1.21.11
required **zero changes** to api/core/data/modules. The only friction was three
mapping renames *inside the adapter* (`ResourceKey.location()→identifier()`,
the `PermissionSet` change). So multi-version support is an **adapter packaging
problem, not a platform problem.** That is exactly what the architecture was
designed to guarantee (AVD Rule Zero, REH §4), now confirmed.

## 2. What actually differs between versions

1. **Mappings / renamed symbols** — the `location()→identifier()` class of
   change. Mojang mappings drift every version.
2. **Removed/added API** — event signatures, registry access, command source
   permission model (ints → `PermissionSet` in 1.21.11).
3. **Fabric API module versions** — per MC version; method surfaces evolve.
4. **NeoForge** — only 1.20.2+; its own event bus and lifecycle per version.
5. **Protocol** (for rock-client / bots) — wire protocol changes per version;
   handled by rock-protocol's version negotiation, independent of the above.

Everything else ROCK relies on — block positions, UUIDs, dimension ids, chat
strings, command dispatch — is stable in *shape* across the matrix even when
the symbol names move.

## 3. Strategy options (with recommendation)

### Option A — A thin version-compat interface, one adapter, multiple impls
Define a small `McBridge` interface in the adapter covering the handful of
version-sensitive operations ROCK needs (resolve dimension id, read block
registry id, set block, check op level, register command, map break/chat
events). Provide one implementation per MC family. Pick at runtime by detecting
the running version.
- **Pro:** one adapter jar can target several versions; clean seam.
- **Con:** reflection or multi-release packaging for symbols that don't exist
  on all versions; can get fiddly where whole event classes differ.

### Option B — Stonecutter (recommended primary)
[Stonecutter](https://stonecutter.kikugie.dev) is the de-facto tool for
multi-version Fabric/NeoForge mods: one source tree with comment-based
conditionals (`//? if >=1.21.11`), building a matrix of per-version jars from a
single project. The whole modded ecosystem (incl. several competitors we
analysed) uses it.
- **Pro:** industry standard; produces a proper per-version jar each admin
  picks like any mod; handles symbol renames and API removals cleanly; works
  for both loaders.
- **Con:** the adapter source gains version annotations; a build matrix to
  maintain. **But it touches only `packaging/`, never the platform.**

### Option C — Separate adapter project per version family
`packaging/fabric-1.20`, `packaging/fabric-1.21`, `packaging/fabric-26`, etc.
- **Pro:** dead simple, total isolation, no preprocessor.
- **Con:** code duplication across adapters (the adapter is small, so this is
  tolerable early on); N Loom builds.

**Recommendation:** start with **C** for the first second version (copy
`packaging/fabric-mod`, retarget, fix the handful of renames — proven fast by
K3), then consolidate to **B (Stonecutter)** once 3+ versions are in play and
duplication hurts. **A** only for narrow runtime-detected differences within one
jar. NeoForge gets the same treatment via ModDevGradle, mirroring Fabric.

## 4. What the platform must guarantee to stay version-free

These are already true; the rule is **keep them true**:
- Adapters convert *to ROCK abstractions at the boundary* — modules never see a
  Minecraft type (enforced by the loader-isolation Checkstyle/Gradle check).
- New world-interaction needs get a **new rock-api event/capability contract**,
  implemented per-adapter — never a Minecraft type leaking inward (AVD Rule Zero).
- Dimension/world identity stays string-keyed (`WorldEventBridge.worldId`),
  which is stable across versions.

## 5. Suggested sequencing (when scheduled)

1. **Pin a compat test matrix:** 1.20.6, 1.21.1, 1.21.11, latest 26.x — one
   representative per family.
2. **Second adapter (Option C):** copy fabric-mod → target 1.21.1; run the K3
   harness against it. This measures the real per-version delta cheaply.
3. **NeoForge adapter:** ModDevGradle equivalent of fabric-mod for one version.
4. **Adopt Stonecutter** once the deltas are catalogued, collapsing the copies
   into one multi-version project per loader.
5. **CI matrix:** the existing K3 harness, parameterised by MC version, on a
   schedule (heavy downloads — not per-commit).

## 6. Cost reality

The platform cost is **zero** (proven). The cost is entirely in `packaging/`:
maintaining per-version adapter builds and a periodic compat test matrix. That
is the price of the abstraction paying off — the expensive, fragile,
version-chasing work is quarantined to the smallest, simplest module in the
whole project.
