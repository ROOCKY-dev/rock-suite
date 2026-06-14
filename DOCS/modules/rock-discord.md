# rock-discord

> Minecraft ↔ Discord **chat bridge** — relays in-game chat to a Discord channel
> (and is the seam for richer Discord integration later). Stdlib-only HTTP; no
> JDA/heavy dependency.

- **Module id:** `rock-discord` · **Depends:** rock-core, rock-data, rock-permissions
- **Status:** functional (outbound chat relay); **early** (see §5).

## 1. What it does
Subscribes to the chat event and forwards messages to a configured Discord
channel through a small `DiscordGateway`. When no token is configured it installs
a **no-op gateway** (logged once) so the module is safe to ship enabled —
nothing breaks on a server without Discord.

## 2. Architecture
- `DiscordGateway` (SPI): `send(channel, content)`. Implementations:
  - `HttpDiscordGateway` — real delivery via Discord's HTTP API (token-auth).
  - no-op — used when `token` is blank.
- `DiscordMessageQueue` — rate-limits sends (configurable interval) so chat
  spikes don't trip Discord limits.

## 3. Configuration (`rock-discord.toml`)
```toml
[discord]
# token = "${env.ROCK_DISCORD_TOKEN}"   # via env, never commit (TRS §11)
chat-bridge-channel = ""                  # empty = bridge disabled
```

## 4. Use-flow
Set `ROCK_DISCORD_TOKEN` + `chat-bridge-channel` → in-game chat appears in the
Discord channel. No token → silently disabled (no-op).

## 5. Status — public-readiness gaps
- **Inbound** Discord→MC relay (gateway/websocket), not just outbound.
- **Slash commands** (`/online`, `/whitelist`, mod actions) — the big one.
- Rich embeds, webhook avatars/usernames, join/leave & death/advancement feeds.
- Account linking (Discord ↔ player) + role sync with rock-permissions groups.
- Per-event channels (chat vs admin alerts vs moderation log).

## 6. API
Other modules can send to Discord by depending on the configured gateway via the
ServiceRegistry (e.g. moderation could post ban notices). Chat relay is automatic.

## Roadmap / upgrade log
- _2026-06-14 · v2.0.0-insiders · documented baseline._
