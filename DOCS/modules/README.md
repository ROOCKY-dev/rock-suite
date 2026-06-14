# ROCK SUITE — Module Documentation

One doc per module: **current features, how they work, use-flow scenarios, and a
public-readiness gap list** — the living baseline the Owner updates as features
ship (each doc ends with a *Roadmap / upgrade log*). Format template: see any
doc; `rock-claims.md` is the fullest reference.

> ROCK works today but is **not yet public-ready** — every doc's *Status* and
> *gaps* section is the punch list toward that. Command polish across all modules
> is tracked separately in [`../COMMAND_FRAMEWORK.md`](../COMMAND_FRAMEWORK.md).

## Modules

| Module | One-liner | In-game commands? |
|---|---|---|
| [rock-permissions](rock-permissions.md) | Permission engine: players/groups/wildcards/contexts/temp/meta | `/rock perms …` |
| [rock-claims](rock-claims.md) | Chunk land protection + trust roles + flags | `/rock claims …` |
| [rock-economy](rock-economy.md) | Audited-ledger economy (balance/pay/baltop) | `/balance /pay /baltop` |
| [rock-essentials](rock-essentials.md) | Homes, warps, TPA | `/home /warp /tpa …` |
| [rock-moderation](rock-moderation.md) | Ban/mute/warn + history, audited | `/ban /mute /warn …` |
| [rock-logging](rock-logging.md) | Block logging + rollback/restore (CoreProtect-class) | `/rock log …` |
| [rock-teams](rock-teams.md) | Teams/guilds identity (backs claims) | **none yet** |
| [rock-discord](rock-discord.md) | MC↔Discord chat bridge | none |
| [rock-backup](rock-backup.md) | Scheduled world+DB backups | **none yet** |
| [rock-metrics](rock-metrics.md) | Platform observability | `/rock metrics` |
| [rock-migrate](rock-migrate.md) | Importers from incumbents (LuckPerms, Essentials…) | `/rock migrate …` |
| [rock-web](rock-web.md) | Web dashboard: REST + JWT + SSE/WS + SPA | none (REST/web) |

Platform projects (not feature modules) are documented elsewhere: `rock-api`
(contracts), `rock-core` (DI/event/command/config runtime), `rock-data`
(JDBI/migrations), `rock-protocol` (projection layer — see `../RFC-001-ROCK-CLIENT.md`).

## Cross-cutting public-readiness themes (recurring in the gap lists)
1. **Command QoL** — tab-completion, clickable/formatted output, real per-arg
   help (the [Command Framework v2](../COMMAND_FRAMEWORK.md) pass).
2. **Missing command surfaces** — rock-teams and rock-backup have none; several
   modules lack list/inspect/admin subcommands.
3. **Client/visual layer** — claim map, inspector overlay, TPA toasts, wallet HUD
   (rock-client Tier 1/2).
4. **Web depth** — write actions + full UI in rock-web.
