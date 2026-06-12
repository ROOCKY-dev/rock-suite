# RFC-001 — rock-client: the native client companion

**Status:** Accepted concept — scheduled (see Phasing); not yet in development
**Type:** GDPM Type C/D (new module + new core networking system)
**Requested by:** Project Owner (Ahmed)

---

## 1. Problem

Every interaction with ROCK today happens through chat commands or (future)
the web dashboard. On the server side that is correct — but the *in-game
experience* is where players live, and the most beloved incumbent features are
client-side: FTB Chunks' map claiming GUI is arguably the single feature that
keeps the FTB stack installed; Prometheus ships a client GUI for editing
roles; Xaero's/JourneyMap integration is how players actually *see* claims.

A server-only platform leaves that entire experience layer to competitors.
Meanwhile nothing about ROCK's architecture requires the client to be vanilla:
Fabric/NeoForge servers can ship an **optional** client mod.

## 2. Vision

**rock-client** is a client-side companion mod that upgrades chat-command
interactions into native GUIs, HUDs, and world-rendered overlays — strictly
optional, strictly nice-to-have, never required.

> A vanilla client must always have 100% of ROCK's functionality through
> commands. rock-client makes the same functionality *delightful*.

## 3. What it would do (feature exploration)

### Tier 1 — the moat-builders
- **Claim map & claiming GUI** (the FTB Chunks killer): full-screen chunk map;
  drag to claim/unclaim; claims colored by owner/team; flag toggles
  (PVP/EXPLOSIONS/MOB_GRIEFING/FAKE_PLAYERS/FIRE_SPREAD) as switches; trust
  management (drag players between ACCESS/CONTAINER/BUILD/MANAGER columns).
- **In-world claim boundaries**: wireframe/particle boundary rendering with
  owner color + entry/exit toast ("Entering Alice's Base — PVP off").
- **Team panel**: roster with roles, invite/kick buttons, team-claim summary,
  shared waypoints (team homes/warps rendered in-world).
- **Admin inspector overlay** (the CoreProtect-killer surface): punch a block
  in inspector mode → floating panel of its rock-logging history; box-select a
  region → rollback **preview rendered as ghost blocks** before confirming.

### Tier 2 — daily QoL
- **Wallet HUD**: balance + last-transaction toasts ("+$25.00 from Alice");
  baltop leaderboard screen; pay dialog with player picker.
- **Homes/warps/TPA UI**: home list with set/teleport/delete buttons; TPA
  request toast with Accept/Deny buttons (no more typing /rock tpaccept in 60s).
- **Permission editor** (admin): searchable node tree per player/group,
  tri-state toggles, context selector — LuckPerms' web editor, in-game.
- **Chat upgrades**: rendered prefixes/suffixes from permission meta, team
  chat tab, mention pings, Discord-bridge indicator on bridged messages.

### Tier 3 — later
- Quest/event toasts from third-party modules (generic notification API),
  marketplace browsing, server metrics overlay for admins (rock-metrics feed).

## 4. Architecture

```text
┌────────────────────── client ──────────────────────┐
│ rock-client (Fabric/NeoForge client mod)           │
│  ├── screens/   GUIs (map, team, wallet, admin)    │
│  ├── render/    world overlays (bounds, ghosts)    │
│  ├── hud/       toasts, wallet, indicators         │
│  └── net/       RockClientProtocol (decode/req)    │
└─────────────▲──────────────────────────────────────┘
              │ custom payload channel "rock:proto/v1"
┌─────────────▼──────────────────────────────────────┐
│ rock-protocol (NEW platform project, server side)  │
│  versioned payload codecs + capability handshake   │
│  (pure Java; loader adapters own the raw channel)  │
├────────────────────────────────────────────────────┤
│ existing modules — UNCHANGED: the protocol layer   │
│ only *projects* ServiceRegistry data outward       │
└────────────────────────────────────────────────────┘
```

Key decisions:

1. **Server-authoritative, always.** The client never computes truth: every
   GUI action becomes the same service call the equivalent command makes, with
   the same permission checks. The client renders *projections*; the server
   validates *intents*. A modified client gains nothing a command spammer
   doesn't already have.
2. **Capability handshake.** On join the client announces protocol version +
   desired feature subscriptions; the server answers with what's enabled and
   permission-filtered. Unknown-version clients degrade to vanilla behavior.
   Subscriptions are push-based (claim entered, balance changed, TPA request)
   on the existing EventBus — the protocol layer is "EventBus over the wire,
   filtered per player".
3. **`rock-protocol` is a platform project, not part of rock-client** — the
   web dashboard's WebSocket feed (RPS §17 networking layer) reuses the same
   payload model. One projection layer, two transports.
4. **Privacy/permission filtering at the edge:** a player only receives claim
   data they could see via commands; admin overlays require the same
   `rock.admin.*`/`rock.logging.*` nodes as the commands they visualize.
5. **Module extension point:** modules contribute payload projections the same
   way they contribute commands today (e.g. rock-logging registers the
   inspector projection) — third-party modules get client surface for free.

## 5. What ships where

| Piece | Repo location | Depends on |
|---|---|---|
| `platform/rock-protocol` | this monorepo | rock-api only |
| protocol channel wiring | `loaders/rock-loader-*` | loader payload APIs (K3 packaging) |
| `rock-client` | **separate repo** (`ROOCKY-dev/rock-client`) | Minecraft client APIs end-to-end — different toolchain (Loom client runs), different release cadence, optional install |

## 6. Risks

- **Client modding is UI-heavy work** — screens/rendering dwarf the protocol
  effort. Mitigate: Tier 1 only at first; map GUI before everything else.
- **Hard dependency on K3 packaging** (real Loom/ModDevGradle): blocked until
  then by definition — hence "future module".
- **Version skew** between client and server: explicit protocol versioning +
  capability handshake from day one (decision 2).
- **Scope seduction:** rock-client must never become a content mod (TRS §20
  non-goals). It renders management; it adds no gameplay.

## 7. Phasing (slots into MODULE_ROADMAP)

1. **v1.4–v1.5 (now):** keep building the data/service surface the client will
   project (inspector queries, rollback previews, punishment history — all
   command-accessible first).
2. **v1.6:** `platform/rock-protocol` — payload model + handshake + per-player
   event projection, fully testable without a client (testbench speaks the
   protocol as a fake client).
3. **v2.0 (post-K3, alongside rock-web):** rock-client repo bootstrap, Tier 1
   (claim map GUI, boundaries, TPA toasts), shared protocol with the web
   dashboard's WebSocket feed.
4. **v2.x:** Tier 2 (wallet HUD, permission editor), module extension API for
   third-party client surface.
