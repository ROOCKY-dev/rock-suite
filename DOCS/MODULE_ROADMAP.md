# ROCK SUITE — Module Improvement & Expansion Roadmap

**Status:** Planning document — work modules one by one, in this order.
**Basis:** COMPETITIVE_ANALYSIS.md. Each item is sized S/M/L and tagged
[parity] (incumbents have it, we must) or [edge] (only ROCK can do it well).

**Ordering rationale:** permissions underpins everything (REH §19) → claims is
the flagship → economy powers claims taxes/rent → discord showcases the event
bus → then platform-level multipliers (web, migration, Paper loader).

---

## 1. rock-permissions (work on first)

### Improvements
- **P1 [parity, M] Contexts** — scope any permission to `world=`, `server=`, or
  `claim=` (the last one is our [edge]: LuckPerms cannot scope to a claim).
  Schema: add nullable `context` column to both permission tables; evaluation
  cache keyed by (subject, context).
- **P1 [parity, S] Meta on groups** — prefix/suffix/weight per group
  (`rock_group_meta` table), exposed via `PermissionService.metaOf(player)`.
- **P1 [parity, M] Temporary permissions/ranks** — `expires` column + lazy
  expiry on read + sweep task. Powers paid-rank servers; trivially feeds
  `RockAuditEntry`.
- **P2 [parity, M] Tracks (promotion ladders)** — ordered group sequences with
  `/rock perms promote <player> <track>`; emits `RankAssignedEvent`.
- **P2 [parity, S] Verbose mode** — `/rock perms verbose on` streams every
  `check()` for a target player with the resolution path (which group/wildcard
  matched). Cheap to build: the calculator already walks an explainable chain.
- **P2 [edge, S] Audit every mutation** — wire grant/deny/group ops into
  `AuditService` (table already exists; LuckPerms' actionlog equivalent for free).
- **P3 [parity, L] Cross-server sync** — pub/sub invalidation (Redis or
  Postgres LISTEN/NOTIFY) so multi-server networks share one permission state.

### Expansions
- **Default groups & first-join assignment** (config: `default-group = "Member"`).
- **Per-claim member permission profiles** — bridges into rock-claims roles (below).

## 2. rock-claims (flagship — work on second)

### Improvements
- **P1 [parity, M] Trust levels / member roles** — per-claim members with
  ACCESS / CONTAINER / BUILD / MANAGER roles (GriefPrevention's proven model);
  `rock_claim_members` table; `ClaimService.trust(claimId, player, role)`.
- **P1 [parity, M] Protection enforcement** — the loader adapters currently map
  join/leave only; add block-break/place/interact/explosion bridge events so
  claims actually protect. Needs `BlockChangeEvent` + `InteractEvent` contracts
  in rock-api and per-loader mappings (the real Loom/ModDev packaging work).
- **P1 [parity, S] Claim-block accrual** — play-time grants claimable chunks
  (GriefPrevention's retention engine); store accrual in `rock_metadata`.
- **P2 [parity, M] In-world visualization** — boundary particles/ghost blocks on
  `/rock claims show`; abstract `BoundaryRenderer` per loader.
- **P2 [parity, M] Sub-claims** — `parentId` on `RockClaim` (ClaimOwner already
  models it); per-subclaim role overrides → Towny plot equivalent.
- **P2 [edge, M] Taxes & upkeep** — scheduled `EconomyService.transfer` from
  claim treasury to server sink; delinquency → claim expiry pipeline. Fully
  audited, unlike every incumbent.
- **P3 [parity, M] Expiration/cleanup** — auto-expire claims of players inactive
  N days (soft-delete + grace period; `lastSeen` already tracked).
- **P3 [parity, M] Map overlays** — BlueMap/squaremap render of `rock_claims`
  (read-only, server-side companion).

### Expansions
- **Towns & Nations (L)** — promote TOWN/NATION ClaimTypes to first-class
  government: membership, ranks via per-claim groups, town treasury, invites.
  This is the Towny-displacement play and justifies the whole platform.

## 3. rock-economy (work on third)

### Improvements
- **P1 [parity, S] Player commands** — `/rock pay`, `/rock balance`,
  `/rock baltop` on the existing CommandService; permission-gated.
- **P1 [parity, S] Currency formatting & config** — symbol, decimals, starting
  balance (config engine already supports live reload).
- **P1 [edge, S] Admin grants/sinks as transactions** — `/rock eco give|take`
  recorded as SYSTEM-owner transfers; zero invisible money creation.
- **P2 [parity, M] Multi-currency** — `currency` column on accounts +
  transactions; gems/votes/event-points without a second plugin.
- **P2 [edge, M] Idempotent transfer API** — client-supplied idempotency key on
  `transfer()` so retries can't double-spend (no incumbent has this).
- **P3 [parity, M] Shop integration surface** — publish a stable
  `EconomyService` artifact + (Phase 2, Paper) a Vault bridge so the entire
  shop-plugin ecosystem works against ROCK unmodified.
- **P3 [edge, M] Interest/decay policies** — scheduled balance policies with
  full transaction trails (server-economy inflation control).

## 4. rock-discord (work on fourth)

### Improvements
- **P1 [parity, M] Chat bridge** — needs `PlayerChatEvent` in rock-api + loader
  mappings; MC→Discord via existing queue, Discord→MC via gateway transport.
- **P1 [edge, S] Domain-event embeds** — config-mapped alerts: claim created,
  big transaction, punishment issued → channel embeds. DiscordSRV needs hooks
  per plugin; we subscribe to our own EventBus once.
- **P1 [parity, M] Link verification flow** — `/rock discord link` issues a
  code, bot DM confirms; require-link mode (kick unlinked) as config.
- **P2 [parity, M] Gateway (WebSocket) transport** — behind the existing
  `DiscordGateway` interface: presence, slash commands (`/online`, `/balance`),
  Discord-side moderation commands.
- **P2 [parity, M] Role sync** — Discord role ↔ RockGroup bidirectional mapping;
  emits the same Rank events as in-game promotion.
- **P3 [parity, S] Console channel** — stream platform log to a staff channel
  with rate-limit-aware batching (queue already does the hard part).
- **P3 [parity, S] Ban sync** — `PunishmentAppliedEvent` ↔ Discord ban API.

## 5. Platform multipliers (after the four modules)

- **RMG migration toolset (L, non-negotiable per Charter):** importers for
  LuckPerms (users/groups/tracks/meta), GriefPrevention (claims+trust),
  Towny (towns/nations/plots → TOWN claims), EssentialsX (balances).
  Each importer is read-only against the source format — no GPL code linked.
- **Loader packaging (M, prerequisite for real-server testing):** wire Fabric
  Loom + NeoForge ModDevGradle in a `packaging/` build so the adapters compile
  against real mapped Minecraft and bundle jar-in-jar; replaces loader-stubs at
  release time. This unblocks an actual server + real-clients smoke test.
- **rock-web dashboard (L, the differentiator):** REST `/api/v1` (players,
  claims, economy, audit) + JWT auth (TRS §9) — no incumbent has a unified one.
- **rock-admin (M):** punishments commands wired to the existing
  `RockPunishment` domain + audit; inspection tools (`/rock who`, claim info).
- **rock-loader-paper (L, Phase 2):** the land-grab into incumbent territory
  once module parity exists; brings the Vault bridge with it.

## 6. Suggested next session

Start with **rock-permissions P1** (contexts, meta, temporary permissions) —
everything else stacks on it, exactly as REH §19 predicted.
