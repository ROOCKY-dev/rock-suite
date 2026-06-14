# Supported Versions

Which Minecraft versions ROCK SUITE publishes for, and how long each is
supported. Policy: [`DOCS/RELEASE_AND_DISTRIBUTION.md`](DOCS/RELEASE_AND_DISTRIBUTION.md) §5.

| Minecraft | Loaders | Tier | Status | Notes |
|---|---|---|---|---|
| **26.x** (latest `26.1`) | Fabric · NeoForge | **Current** | adapter in progress | newest features land here first |
| **1.21.x** (latest) | Fabric · NeoForge | **LTS** | ✅ supported | modern modded anchor; primary LTS |
| **1.20.1** | Fabric · NeoForge | **Legacy LTS** | ✅ security/critical | large existing modpack base |
| ≤ 1.19.x | — | EOL | ❌ | community PRs only |

Runtime: **Java 21**. The platform jars (api/core/data/protocol + modules) are
Minecraft-agnostic — one build runs on every supported version; only the small
loader adapter is per-version.

**Tiers**
- **Current** — newest MC; gets new features first.
- **LTS** — long-term: every Stable patch backported; supported well past the
  next MC drop so you're never forced to upgrade.
- **Legacy LTS** — security & critical bugfixes only; no new features.

**Proven so far:** real-server + protocol-client checks pass on **1.21.11**
(Fabric & NeoForge) and **1.20.6** (Fabric). The `26.x` and `1.20.1` published
targets follow the same adapter mechanism.
