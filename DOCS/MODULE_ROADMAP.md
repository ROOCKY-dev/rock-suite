# ROCK SUITE — Module Improvement & Expansion Roadmap (v2)

**Status:** Planning document. The DOCS are guardrails, not limits — ROCK is a
modular, abstracted server-management platform; the module portfolio grows
wherever server admins currently need a third-party mod.
**Basis:** COMPETITIVE_ANALYSIS.md v2 (Fabric/NeoForge-native + Bukkit incumbents).
Sizes S/M/L; [parity] = incumbents have it; [edge] = only ROCK can do it well.

---

## 0. Platform keystones (unlock everything else)

- **K1 [edge, M] World-interaction event layer** — cancellable
  `BlockChangeEvent` / `PlayerInteractEvent` contracts in rock-api + loader
  mappings, with **fake-player classification** (modded reality). This single
  layer powers claims protection, block logging, and moderation. *(shipping in v1.1.0)*
- **K2 [parity, M] fabric-permissions-api provider + NeoForge PermissionAPI
  provider** — every mod's permission checks route into rock-permissions with
  zero integration work. The highest-ROI bridge on modded.
- **K3 [parity, L] Real loader packaging** — Loom + ModDevGradle builds
  replacing loader-stubs at release; unblocks on-server smoke tests.
- **K4 [edge, M] WorldMutator abstraction** — loader-provided block get/set
  for rollback/restore operations. *(shipping in v1.1.0)*

## 1. rock-permissions
- **P1 [parity, M] Contexts as composable conditions** — world/dimension/
  server/creative/claim scoping; FTB Ranks-style And/Or/Not composition.
  `claim=` context is our [edge].
- **P1 [parity, S] Typed permission values** — numeric/string nodes
  (`rock.essentials.homes.max = 5`) so module limits live in one system.
- **P1 [parity, S] Prefix/suffix/weight meta** on groups.
- **P1 [parity, M] Temporary permissions/ranks** (expiry + sweep).
- **P2 [parity, M] Tracks/promotion ladders**; **P2 [parity, S] verbose
  debugger**; **P2 [edge, S] audit every mutation** (AuditService wiring).
- **P3 [parity, L] Cross-server sync** (pub/sub invalidation).

## 2. rock-claims
- **P1 [parity, M] Trust roles** — ACCESS/CONTAINER/BUILD/MANAGER members
  per claim. *(shipping in v1.1.0)*
- **P1 [parity, M] Protection enforcement** — listener on the K1 event layer,
  in-memory claim index for tick-thread-safe lookups. *(shipping in v1.1.0)*
- **P1 [parity, M] Claim flags** — Cadmus-style toggles (pvp, explosions,
  mob-griefing, fake-player-allow) per claim.
- **P2 [parity, M] Registry-driven protection categories** — Flan's lesson:
  unknown modded blocks/items fall into default-deny categories.
- **P2 [parity, S] Claim-block accrual**, **[parity, M] visualization**
  (particles), **[parity, M] sub-claims**, **[parity, S] force-loading**.
- **P2 [edge, M] Taxes/upkeep** via EconomyService (fully audited).
- **P3 [parity, M] Expiration of inactive claims** (OPAC model),
  **[parity, M] map-mod sync** (Xaero's/BlueMap overlays).
- **P3 [edge, L] Towns & Nations** — government layer on TOWN/NATION types.

## 3. rock-economy
- **P1 [parity, S] Player commands** (/rock pay, balance, baltop) +
  currency formatting config.
- **P1 [edge, S] Admin grants as SYSTEM transactions** (no invisible money).
- **P2 [parity, M] Multi-currency**; **P2 [edge, M] idempotent transfers**.
- **P3 [parity, M] Shop surface** (published artifact + Vault bridge on Paper).

## 4. rock-discord
- **P1 [parity, M] Chat bridge** (needs PlayerChatEvent contract + loader map).
- **P1 [edge, S] Domain-event embeds** (claims/economy/punishments → channels).
- **P1 [parity, M] Link verification + require-link mode.**
- **P2 [parity, M] Gateway transport + slash commands; role↔group sync.**
- **P3 [parity, S] Console channel; ban sync.**

## 5. NEW MODULES (portfolio expansion)

### rock-logging — block/container logging & rollback ⭐ the wedge module
*(core shipping in v1.1.0)* Competitors: CoreProtect (reference), Ledger.
- **P1** Action log on the K1 event layer: block break/place with actor,
  position, before/after states; async **batched consumer** (CoreProtect's
  queue model on our DataService.batch).
- **P1** Query grammar: time window / actor / radius / action / world.
- **P1** Rollback & restore via WorldMutator, **rolled-back flag** kept (re-rollbackable).
- **P2** Container item-flow tracking (Ledger's item insert/remove actions);
  inspector mode (`/rock log inspect` — click a block, see history);
  rollback **preview** before apply.
- **P2 [edge]** Claims-aware queries: "rollback everything non-members did in
  this claim" — impossible for every incumbent.
- **P3** Purge with retention policy (TRS §10's 90-day default); entity logs.

### rock-teams — parties/guilds as first-class identity
Competitors: FTB Teams, Argonauts, OPAC parties.
- **P1** Team CRUD, invites, member ranks; `GroupOwner` already models team
  ownership of claims & accounts — wire it through.
- **P2** Team chat channel; ally relations; team-scoped permissions via the
  `claim=`/`team=` context.
- **P3** Team map markers, cross-server teams.

### rock-essentials — admin/player QoL kit
Competitors: FTB Essentials, EssentialCommands.
- **P1** Homes (limit via typed permission), warps, spawn, TPA (request flow),
  back-on-death.
- **P2** Kits (cooldowns via metadata), nicknames (chat meta), mute
  (RockPunishment.MUTE wiring), invsee/enderchest.
- **P3** RTP, leaderboards (playtime from rock_players).

### rock-moderation — beyond punishments
- **P1** Punishment commands (/rock ban|mute|warn with durations) on the
  existing RockPunishment domain + enforcement at loader join/chat.
- **P2** Vanish, freeze, inventory inspection, grief-alerts (rock-logging
  feed → staff + Discord).

### rock-backup — scheduled world+DB backups (TRS §14 requires it)
- **P1** Scheduled snapshots (world dir + SQL dump), retention, restore CLI.
- **P2** Off-site targets (S3-compatible), pre-restore safety backup.

### rock-metrics — observability (RPS §13)
- **P1** TPS/memory/event-throughput/DataService-latency counters exposed on
  the platform; spark detection (integrate, never duplicate).
- **P2** Prometheus exporter endpoint; per-module tick budget tracking
  (TRS §3's 0.25 ms/tick budget made observable).

### rock-webmap — map-layer integration (not a map)
- **P2** BlueMap/squaremap/Xaero's claim overlays + team colors, fed from the
  claim index. We integrate with maps; we do not build one.

### rock-client — native client companion ⭐ the experience moat (future)
Full concept: **RFC-001-ROCK-CLIENT.md**. Optional client mod turning command
interactions into GUIs/HUDs/overlays — claim map & drag-claiming (the FTB
Chunks killer), in-world boundaries, TPA toasts, wallet HUD, admin inspector
overlay with ghost-block rollback preview, in-game permission editor.
- Prereq `platform/rock-protocol` (server-side payload projection, shared
  with the web dashboard's WebSocket feed) lands **v1.6**; the client mod
  itself ships **v2.0** in its own repo, after K3 packaging.
- Law: vanilla clients always retain 100% functionality via commands.

## 6. Platform multipliers (unchanged priorities)
- **RMG migration importers** — now including modded sources: FTB Chunks/
  Ranks/Teams, Flan, OPAC, Ledger history; plus LuckPerms, GriefPrevention,
  Towny, EssentialsX.
- **rock-web dashboard** — REST /api/v1 + JWT; the surface no competitor has.
- **rock-loader-paper** — Phase 2 land grab; brings the Vault bridge.

## 7. Execution order

1. **v1.1.0 (now):** K1 + K4 keystones, claims trust+protection, rock-logging core.
2. **v1.2.0:** K2 permission providers, permissions P1 set (contexts, typed
   values, meta, temp), claim flags.
3. **v1.3.0:** rock-teams, rock-essentials P1, economy P1, discord chat bridge.
4. **v1.4.0:** rock-moderation, logging P2 (containers, inspector, preview),
   K3 real packaging → first on-server release.
5. **v1.5.0:** rock-backup, rock-metrics, webmap overlays, migration importers.
6. **v1.6.0:** platform/rock-protocol (client/web projection layer, RFC-001).
7. **v2.0:** rock-client Tier 1 + rock-web, sharing the protocol layer.
