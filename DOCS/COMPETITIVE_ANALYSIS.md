# ROCK SUITE — Competitive Analysis (v2)

**Status:** Working document — informs MODULE_ROADMAP.md
**Method:** Structural review of competitors' open-source trees (shallow clones,
inspection only — no GPL/ARR code enters this Apache-2.0 codebase). Closed-source
competitors assessed from public documentation.
**v2 change:** added the analysis that actually matters first — the
**Fabric/NeoForge-native ecosystem** ROCK launches into, including the
"server admin survival kit" no admin lives without.

---

# Part I — The home turf: Fabric/NeoForge-native competitors

These are the mods ROCK displaces (or must interoperate with) on its Tier-1
platforms. This is the primary battlefield; the Bukkit incumbents in Part II
are the long game.

## 1. The modded-server admin survival kit

What a Fabric/NeoForge admin actually installs today, and where ROCK stands:

| Need | Today's pick | ROCK answer | Status |
|---|---|---|---|
| Permissions | **LuckPerms (Fabric/NeoForge)** or **FTB Ranks** | rock-permissions | ✅ v1, gaps below |
| Permission API hook | **fabric-permissions-api** (every mod checks it) | provider bridge | ❌ **critical gap** |
| Claims + protection | **FTB Chunks**, **Flan**, **OPAC**, **Cadmus** | rock-claims | ⚠️ storage only — no protection enforcement yet |
| Block logging / rollback | **Ledger** (Fabric), CoreProtect (Bukkit ref.) | — | ❌ **the #1 must-have, missing** |
| Teams / parties | **FTB Teams**, **Argonauts** | — | ❌ missing (claims/economy already model group owners) |
| Homes/warps/TPA/kits | **FTB Essentials**, **EssentialCommands** | — | ❌ missing |
| Discord bridge | **DiscordIntegration** (multi-loader) | rock-discord | ⚠️ no chat bridge yet |
| Web map | **BlueMap / Dynmap / squaremap** | overlay integration | ❌ missing |
| Profiler | **spark** | out of scope (integrate, don't compete) | — |
| World pregen | **Chunky** | out of scope | — |
| Backups | ad-hoc scripts / FTB Backups | rock-backup (TRS §14 requires it) | ❌ missing |

**Read of the table:** ROCK v1 proves the platform, but an admin today still
needs 6+ third-party mods. Each row we fill converts directly into adoption;
the rows marked "out of scope" (spark, Chunky) we should *detect and
integrate with*, never duplicate.

## 2. FTB stack (Chunks + Ranks + Teams + Essentials)

**Observed structure:** the closest thing to ROCK that exists — a suite with
shared team identity, but **without** a shared data layer or platform: each mod
ships its own storage (NBT/JSON files), its own config, its own API.

- **FTB Chunks:** chunk claims + force-loading, full **client-side map +
  minimap + waypoints GUI** (huge part of its appeal), claim API with
  fabric/neoforge adapters. Claims belong to FTB Teams, not players.
- **FTB Ranks:** condition-based ranks — `DimensionCondition`, `OPCondition`,
  `CreativeModeCondition`, `FakePlayerCondition`, composable `And/Or/Not`
  conditions; typed permission values (boolean/number/string, e.g.
  `ftbessentials.homes.max: 5`); chat `MessageDecorator` (prefix/format).
- **FTB Teams:** party layer (allies, member ranks, team chat) underpinning Chunks.
- **FTB Essentials:** homes/warps/TPA/kits/nick/mute/speed/near/leaderboards/
  virtual invsee — the QoL command set admins expect.

**Lessons to adopt:** condition-based permissions (= our contexts, but
composable), **numeric permission values** (limits like `max-homes` belong in
the permission system, not per-module config), fake-player handling (modded
servers are full of machine-controlled fake players — every protection and
permission check must classify them).

**Their weakness:** flat-file storage, no audit trail, no cross-mod
transactions, no web surface, team≠player identity confusion. ROCK's unified
domain model beats this structurally.

## 3. Flan (claims, Fabric/Forge)

**Observed:** block-precise cuboid claims (`ClaimBox`), per-claim permission
groups with player assignments, **`ObjectToPermissionMap`** — any registry
object (block/item/entity) maps to a generated permission, so new modded
blocks are automatically protectable; per-claim "allow lists" for specific
registry entries; global per-dimension claims; particle boundary display;
**`OtherClaimingModCheck`** — it actively detects competing claim mods.

**Lessons:** block-precision matters on modded servers (machines!), and
protection must be **registry-driven** so unknown modded blocks/items are
covered by default-deny categories rather than hardcoded lists. Our
`BoundsType.BLOCK_CUBOID` future-slot is validated.

## 4. Open Parties and Claims (OPAC)

**Observed:** party system + claims with **claim expiration**, chunk
force-loading as a claim feature, deep `protection/` package (per-action
granularity), per-player claim storage with NBT serialization, request-based
async claim operations. Designed for very large servers; syncs with Xaero's
map for in-map claim display.

**Lessons:** claim expiration tied to player activity is table stakes;
force-loading is a claims-adjacent feature admins expect; map-mod sync
(Xaero's/JourneyMap) is the modded equivalent of BlueMap overlays.

## 5. Cadmus + Prometheus + Argonauts (Team Resourceful stack)

**Observed:** the *newest* integrated attempt — Cadmus (claims with a
**flag system**: per-claim toggleable rules), Prometheus (roles/permissions
with **client GUI for editing roles + options**), Argonauts (guilds/parties).
Shared `api/` conventions across the three, but again: separate mods, separate
storage, no ledger/audit.

**Lessons:** claim **flags** (pvp on/off, mob-griefing, explosions…) are the
modern UX for claim rules; an in-game GUI for rank editing is a
differentiator worth copying (we additionally get the web dashboard).

## 6. Ledger (Fabric) + CoreProtect (Bukkit reference) — block logging

**Ledger observed:** Kotlin, registry of `ActionType`s (block break/place/
change, entity kill/change, item insert/remove/pickup/drop — i.e. **container
theft tracking**), `ActionSearchParams` query model, **rollback preview**,
paginated search commands, SQLite via Exposed.

**CoreProtect observed (the gold standard):** dedicated **consumer thread** —
all writes go to an in-memory queue drained in async batches (survives write
bursts); inspector mode (click a block → history); lookup/rollback/restore
with radius/time/user/action filters; purge; WorldEdit integration; per-action
normalized tables.

**Lessons (this is our rock-logging blueprint):** async batched consumer
(maps perfectly onto our `DataService.batch`), action-type registry,
inspector-on-click UX, preview-then-apply rollback, time/radius/actor/action
query grammar. **No competitor links logs to a unified identity/audit/claims
model — we can answer "who griefed this claim and roll it back" in one query.**

## 7. fabric-permissions-api (lucko) — the integration keystone

**Observed:** tiny API — `Permissions.check(source, node, default)`,
`PermissionCheckEvent`, offline checks, options (string meta). **Every serious
Fabric mod routes its permission checks through this.** If rock-permissions
registers a `PermissionCheckEvent` provider, *every mod on the server*
instantly uses ROCK permissions with zero integration work. NeoForge has the
equivalent `PermissionAPI` (nodes registered at startup).

**Action:** loader adapters MUST ship these providers. This single bridge is
worth more adoption than any feature.

## 8. DiscordIntegration (ErdbeerbaerLP)

**Observed:** the multi-loader (fabric/forge/neoforge/quilt) Discord bridge —
chat both ways via mixins, linking, command relay, compatibility shims for
chat-format mods (StyledChat). Confirms rock-discord's parity list (chat
bridge, linking flow) applies on modded too, and that chat events need a
first-class platform contract (mixin-free for us: the loader adapter owns it).

---

# Part II — Bukkit incumbents (the long game)

*(unchanged from v1 — full detail retained below)*

| Domain | Incumbent | Their moat | Their weakness |
|---|---|---|---|
| Permissions | **LuckPerms** | Contexts, web editor, cross-server sync, every platform | No shared data model with anything else |
| Claims | **GriefPrevention** | Simple UX, claim-block accrual, visualization | Bukkit-only, aging |
| Claims (org) | **Towny** | Towns→Nations government, plot economy | Sprawling, Bukkit-only |
| Claims (paid) | **Lands** | Polished GUI, rentals, taxes, wars | Closed, paid, Bukkit-only |
| Economy | **EssentialsX + Vault** | 167-command gravity; Vault is the de-facto API | Flat balances, no ledger/audit |
| Discord | **DiscordSRV** | Bidirectional chat, alerts engine, linking | Hook-per-plugin integration model |

Key LuckPerms features to reach parity with: contexts (server/world),
prefix/suffix meta stacking, tracks/ladders, temporary nodes, verbose
debugger, web editor, bulk update, cross-server messaging sync.
GriefPrevention: trust levels (Access/Container/Build/Manager), claim-block
accrual, visualization, expiration. Towny: government hierarchy, plot
economy, jail, invites. DiscordSRV: alerts engine, console channel,
require-link, role sync.

---

# Part III — Strategic conclusions (v2)

1. **The modded ecosystem has suites but no platform.** FTB and Team
   Resourceful both prove demand for integrated stacks — and both stop at
   flat files, no audit, no ledger, no web. ROCK's data platform is the
   structural win; their feature lists are our parity checklist.
2. **Two integration keystones decide Fabric adoption:** a
   fabric-permissions-api provider (every mod's checks route to ROCK) and
   protection events that respect modded reality (fake players,
   registry-driven block/item categories).
3. **Block logging is the wedge module.** CoreProtect/Ledger is the one mod
   *every* admin installs first. A rock-logging module with
   claims-aware queries ("rollback this claim") + unified identity is an
   immediately superior product and showcases the whole platform.
4. **Teams must be first-class.** FTB Teams/Argonauts/OPAC parties all exist
   because claims & chat & maps need group identity. Our `OwnerReference`
   already models it (`GroupOwner`) — a rock-teams module turns three
   incumbent mods into one ROCK module.
5. **Essentials-QoL is cheap adoption surface.** Homes/warps/TPA/kits on the
   existing command framework + permissions numeric limits.
6. **Integrate, don't fight, the observability/map layer:** spark, BlueMap/
   Xaero's — detect and feed them (claim overlays, metrics export).
7. **Migration importers now include modded sources:** FTB Chunks/Ranks,
   Flan, OPAC, Ledger (log history), plus the Bukkit set from v1.

Actionable backlog: **MODULE_ROADMAP.md** (v2).
