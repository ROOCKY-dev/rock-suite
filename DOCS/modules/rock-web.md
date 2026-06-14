# rock-web

> The **web dashboard** — REST API + JWT auth + live feeds + a single-page UI.
> The differentiator no incumbent offers: manage/observe the server from a
> browser. Zero web-framework dependency (JDK `HttpServer` + hand-rolled JWT/WS).

- **Module id:** `rock-web` · **Depends:** rock-core, rock-data (soft: rock-protocol)
- **Status:** functional backend + a basic SPA; **needs UI depth + more endpoints** (§7).

## 1. What it does
Runs an HTTP server exposing a versioned REST API under `/api/v1`, an SSE event
feed, and (when rock-protocol is present) a WebSocket projection feed — plus a
static dashboard SPA. Auth is JWT (HS256), passwords are Argon2id. All on
virtual threads, never the tick thread.

## 2. REST API (`/api/v1`)
| Method · Path | Auth | Returns |
|---|---|---|
| `POST /auth/login` | public | `{accessToken, refreshToken, role}` |
| `POST /auth/refresh` | public | fresh tokens |
| `GET /me` | USER | `{user, role}` |
| `GET /players` | USER | online players |
| `GET /economy/baltop` | USER | top balances |
| `GET /audit` | **ADMIN** | recent audit entries |

Read endpoints project the same services the in-game commands use — one data
model, two faces.

## 3. Live feeds
- **SSE** `GET /api/v1/events?token=…` — join/leave/transaction/claim/punishment
  events (EventSource can't set headers, so a `?token=` query param is accepted).
- **WebSocket** `ws://host:<protocol-port>/?token=…` — the rock-protocol
  per-player, capability-gated projection feed (same stream the in-game client
  gets); requires the JWT account to be linked to a player.

## 4. The SPA
`web/index.html` (vanilla JS, no build step), served at `/`: login → dashboard
(players online, top balances, audit [admin], live activity), with status LEDs
for the SSE + protocol connections. Verified in a real headless browser.

## 5. Auth & accounts
- `JwtCodec` — HS256 access + refresh tokens, expiry, constant-time verify.
- `PasswordHasher` — Argon2id (password4j).
- `WebAccount`: id, username, password hash, role (`ADMIN`/`USER`), optional
  linked player id. **Bootstrap admin** created on first run from
  `ROCK_WEB_ADMIN_PASSWORD`.

## 6. Configuration (`rock-web.toml`)
```toml
[web]
enabled = true
port = 8080
protocol-port = 8081            # WebSocket feed; default http+1
# jwt-secret = "${env.ROCK_WEB_JWT_SECRET}"
access-token-minutes = 15
refresh-token-days = 7
bootstrap-admin = "admin"
# bootstrap-admin-password via ${env.ROCK_WEB_ADMIN_PASSWORD}
```
Data: `rock_web_accounts` (migration V014).

## 7. Status — public-readiness gaps
- **More endpoints + write actions:** claims, moderation actions (ban/mute from
  the panel), permission editing, live config editing, metrics/graphs, server
  console/log streaming.
- **UI depth:** the SPA is a functional shell — needs full pages, account
  management (create/disable users, link player), pagination, theming.
- **Hardening:** HTTPS / reverse-proxy guidance, rate limiting, CSRF/origin
  policy review, refresh-token rotation/revocation, audit of the web actions.
- This is where most v2.x "make it shine" work lands.

## 8. API surface for extension
Routes are plain `Route` records (`method`, `path`, requiredRole, handler) in
`WebRoutes`; a module-contributed route registry is a natural next step so
modules add their own endpoints like they add commands.

## Roadmap / upgrade log
- _2026-06-14 · v2.0.0-insiders · documented baseline._
