# ROCK SUITE — Competitive Analysis

**Status:** Working document — informs MODULE_ROADMAP.md
**Method:** Structural review of each competitor's open-source tree (shallow clones,
inspection only — no GPL code enters this Apache-2.0 codebase). Closed-source
competitors (Lands, premium claim plugins) assessed from public documentation.

---

## 1. The competitive landscape in one table

| Domain | Incumbent | License | Maturity | Their moat | Their weakness |
|---|---|---|---|---|---|
| Permissions | **LuckPerms** | MIT | Extremely high | Contexts, web editor, cross-server sync, every platform | Standalone product; no shared data model with anything else |
| Claims | **GriefPrevention** | MIT-ish | High | Dead-simple UX, claim-block economy, visualization | Bukkit-only, aging codebase, no towns/nations layer |
| Claims (org) | **Towny** | Custom | Very high | Towns→Nations government layer, plot economy, war addons | Sprawling config, Bukkit-only, monolithic |
| Claims (paid) | **Lands** | Closed | High | Polished GUI, wars, rentals, taxes, map integrations | Closed source, paid, Bukkit-only |
| Economy | **EssentialsX + Vault** | GPL | Very high | 167 commands of utility gravity; Vault is the de-facto economy API | Economy itself is primitive (flat balances, no transactions/audit) |
| Discord | **DiscordSRV** | GPL | Very high | Bidirectional chat, console channel, alerts engine, linking, voice module | JDA-heavy, Bukkit-centric, config sprawl |

**The structural gap ROCK attacks:** every one of these is a silo with its own
storage, config, permission checks, and integration bridges. None of them share a
data model. LuckPerms runs on Fabric/NeoForge, but GriefPrevention, Towny,
EssentialsX, and DiscordSRV are effectively Bukkit-only — **on modern modded
servers (Fabric/NeoForge) the "full management stack" simply does not exist.**
That is ROCK's beachhead.

---

## 2. LuckPerms (vs rock-permissions)

**Architecture observed:** `api/` + `common/` core with thin per-platform adapters
(bukkit, bungee, fabric, forge, neoforge, sponge, velocity, nukkit, standalone) —
structurally the same play ROCK is making, proving the model scales.
`common/` packages that matter: `calculator` (permission resolution chain),
`cacheddata` (per-player resolved cache), `context` (server/world/conditional
scoping), `verbose` (live check debugging), `webeditor`, `actionlog`,
`messaging` (Redis/pluginmsg cross-server sync), `bulkupdate`, `treeview`,
`metastacking` (prefix/suffix), `track` (promotion ladders), `extension`.

**Table-stakes features we lack:** contexts (world/server-scoped permissions),
prefix/suffix meta, tracks/ladders, temporary permissions, verbose debugging,
bulk edit, web editor, cross-server sync.

**Where ROCK can win:** LuckPerms knows nothing about claims, economy, or
Discord. ROCK permissions are evaluated against the *same* identity record that
owns claims and accounts — e.g. "claim members get `rock.claims.member.*`
inside their claim's context" is native for us and a bridge-plugin nightmare
for them. Their own `RockGroup`-equivalent has no audit trail; ours writes
`RockAuditEntry` rows for free.

## 3. GriefPrevention + Towny + Lands (vs rock-claims)

**GriefPrevention observed:** claim-block accrual (play time → claimable area),
boundary visualization, automatic claim extension, expiration/cleanup of
inactive claims, resize events, trust levels (Access/Container/Build/Manager),
claim inspection, sub-claims, pre/post events for everything.

**Towny observed:** `object/` model is a full government stack — Resident, Town,
Nation, District, PlotGroup, jail, spawn points, invites, economy accounts per
town (`EconomyAccount`, `Government`), metadata API, HUDs/map rendering,
status screens. The lesson: organizational claims (town → nation hierarchy)
plus plot-level economy (taxes, plot rent) is what retains large communities.

**Lands (closed, from docs):** GUI-first management, land rentals/taxes, wars,
role-based member permissions per claim, BlueMap/Dynmap/squaremap overlays.

**Table-stakes we lack:** per-claim member roles/trust levels, claim
visualization in-world, claim-block/size economy, sub-claims, expiration of
abandoned claims, taxes/rent, map plugin overlays.

**Where ROCK can win:** our `OwnerReference` + `ClaimType` (PLAYER/TOWN/FACTION/
NATION) already models what Towny bolts on; our claims share the economy
ledger (town treasury = `RockEconomyAccount` with `ClaimOwner`), so taxes are a
scheduled `EconomyService.transfer` with full transaction history — Towny's
economy has no audit trail at all. And none of the three run on Fabric/NeoForge.

## 4. EssentialsX + Vault (vs rock-economy)

**Observed:** EssentialsX economy is flat per-player balances + 167 utility
commands; Vault is the lingua-franca API every shop plugin codes against.
EssentialsDiscord/DiscordLink ship as separate modules.

**Table-stakes we lack:** player commands (`/pay`, `/balance`, `/baltop`),
admin grants, configurable currency formatting, shop integration surface.

**Where ROCK can win:** we already have what they fundamentally lack — a real
double-entry-style ledger: `RockTransaction` with PENDING/COMPLETED/FAILED/
REVERSED states, linked reversals, non-player accounts (town treasuries,
server sink), and atomic transfers. Their model cannot answer "where did this
money come from" — ours answers it by design. A Vault-bridge (on Paper, later)
or a published `EconomyService` makes every shop plugin a free integration.

## 5. DiscordSRV (vs rock-discord)

**Observed:** bidirectional chat bridge, console channel, account linking with
`requirelink` gating (kick unlinked players), ban sync, death/advancement
messages, an **alerts engine** (config-defined triggers → Discord embeds),
voice proximity module, extensive hook ecosystem.

**Table-stakes we lack:** chat bridge (both directions), join/leave/death
embeds, slash commands on the Discord side, require-link enforcement, role
sync (Discord role ↔ ROCK group).

**Where ROCK can win:** DiscordSRV integrates *outward* via fragile hooks into
each plugin. ROCK's Discord module subscribes to the platform EventBus, so
every domain event (claims, economy, punishments, audit) is broadcastable with
zero per-plugin glue. Role sync maps Discord roles to `RockGroup`s natively.
Our stdlib REST transport keeps the jar tiny; a gateway (WebSocket) transport
slots behind the existing `DiscordGateway` interface when slash commands and
presence are needed.

---

## 6. Strategic conclusions

1. **Fabric/NeoForge first is correct.** Four of five incumbents don't run
   there. ROCK should be *the* management stack for modded servers before
   fighting on Paper (where the incumbents are entrenched; a Paper loader
   adapter is the Phase-2 land grab once parity exists).
2. **Integration is the product.** Feature-by-feature parity with five mature
   projects is unwinnable in the short term; "one identity, one ledger, one
   audit trail, one config, one dashboard" is unmatchable by any of them
   individually.
3. **Migration tooling is non-negotiable** (Charter already says so): importers
   for LuckPerms (users/groups/tracks), GriefPrevention (claims),
   Towny (towns/plots → TOWN claims), EssentialsX (balances) remove the
   switching cost that protects every incumbent.
4. **The web dashboard is the differentiator with no real competitor** —
   LuckPerms' web editor is the only comparable surface and covers
   permissions only. Ship it in 1.x, not "someday".
5. **Adopt their best ideas, not their code:** LuckPerms' contexts + verbose
   debugger, GriefPrevention's claim-block accrual + visualization, Towny's
   government hierarchy, DiscordSRV's alerts engine. All are re-implementable
   cleanly on the ROCK event/data model (and our licenses must stay clean:
   nothing GPL-derived may be copied in).

Per-module actionable backlogs: see **MODULE_ROADMAP.md**.
