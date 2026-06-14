# Changelog

All notable changes to ROCK SUITE. Versioning note: the v1.x→v2.0 line is the
**insider** track; the public release restarts at v1.0.0-beta (≡ v2.0.0
insiders).

## v2.0.0 — insiders (2026-06-14)

The client/experience tier and multi-version proof — the platform's projection
layer goes live on the wire, gains a browser face, an in-game companion, and is
proven across two Minecraft families on both Tier-1 loaders.

### Added
- **rock-protocol on the wire (K5).** The projection layer (`platform/rock-protocol`)
  now drives real transports over the `rock:protocol` custom-payload channel:
  - Inbound half: `ProtocolHub.receive` + server-authoritative intent dispatch
    (live session + capability re-check), an extensible `registerIntent`, and
    built-ins `session.ping`→`pong` and `claims.list`→`claim.list.end`.
  - Multi-transport: the hub fans out to many transports at once; the SPI
    (`ProtocolTransport`) and the `ProtocolGateway` contract moved to **rock-api**
    so feature modules can contribute a transport.
  - **Fabric** + **NeoForge** loader transports (custom-payload channel) — proven
    on real servers with a protocol-aware client, 6/6 checks each.
- **rock-web WebSocket feed.** A zero-dependency RFC 6455 server (JDK socket +
  SHA-1/Base64 handshake + frame codec, virtual threads) that delivers the
  per-player, capability-gated projection feed to browsers; JWT-authenticated.
- **rock-web dashboard SPA** — a self-contained single-page app (players online,
  top balances, audit, live activity) served by the WebServer, consuming
  REST + SSE + the protocol WebSocket. Verified in a real headless browser.
- **rock-client** (Fabric companion mod, RFC-001 Tier-1 seed): wallet HUD, claim
  map screen, entry/balance toasts — server-authoritative; vanilla clients keep
  100% functionality. Seeds the separate `ROOCKY-dev/rock-client` repo.
- **Multi-version matrix** (`MULTIVERSION_SUPPORT` §7): the same adapter,
  retargeted to **Minecraft 1.20.6** (`packaging/fabric-1.20`) — the byte-
  identical platform jars boot on both 1.20.6 and 1.21.11; only a 5-edit
  adapter delta differs. 1.20.6 + 1.21.11 are runtime-proven (Fabric); NeoForge
  1.21.11 too. (26.x pending upstream release.)

### Changed
- Platform hardening: `DataMigrator` resolves migrations via a committed index +
  temp-dir extraction (loader-portable; was already shipped in the K4 line).

### Notes
- Cross-loader thesis proven end-to-end (server **and** client seam) on both
  Tier-1 loaders, across two MC families.
- Mod publishing (Modrinth/CurseForge) begins from this insiders milestone.
