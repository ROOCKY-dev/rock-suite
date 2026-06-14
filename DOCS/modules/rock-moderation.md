# rock-moderation

> Player punishments — ban, mute, warn — with durations, history, and a full
> audit trail. Enforced server-side (ban gate on join, mute silences chat).

- **Module id:** `rock-moderation` · **Depends:** rock-core, rock-data, rock-permissions
- **Service:** `dev.rock.api.services.PunishmentService`
- **Status:** functional; **not yet public-polished** (see §8).

## 1. What it does
Issues and tracks punishments. Each is an audited record (issuer, reason,
expiry, revocation). Active bans block login; active mutes drop chat — enforced
by the loader adapter via the session/world-event layer.

## 2. Concepts
- `PunishmentType`: `BAN` · `MUTE` · `WARN`.
- `RockPunishment`: type, target, issuer (`OwnerReference`; `SYSTEM` for console),
  reason, `expires` (null = permanent), `revokedAt`, `activeAt(now)`.
- **Durations:** `30m`, `2h`, `7d`, `perm` (parsed by `DurationParser`; an
  unparseable 2nd arg is treated as the start of the reason).

## 3. Commands
| Command | Permission | Behaviour |
|---|---|---|
| `/rock ban <player> [duration] [reason]` (alias `/ban`) | `rock.moderation.ban` | Bans; permanent if no duration. |
| `/rock unban <player>` | `rock.moderation.ban` | Lifts the active ban. |
| `/rock mute <player> [duration] [reason]` (alias `/mute`) | `rock.moderation.mute` | Mutes (silences chat). |
| `/rock unmute <player>` | `rock.moderation.mute` | Lifts the active mute. |
| `/rock warn <player> <reason>` | `rock.moderation.warn` | Records a warning. |
| `/rock history <player>` | `rock.moderation.history` | Lists punishments with state (ACTIVE/expired/revoked). |

**[QoL gaps]:** no player/duration tab-completion; plain-text history (no
clickable revoke/details); **no `/kick`, IP-ban, tempban presets, ban-on-offline
lookup of unseen players, appeals/notes**; ban screen message not customizable.

## 4. Permissions
`rock.moderation.ban` · `.mute` · `.warn` · `.history` · `rock.moderation.*`.

## 5. Enforcement
- **Login:** the adapter calls the session layer's `joinDenialReason(uuid)`;
  an active ban disconnects with the reason.
- **Chat:** an active mute makes `playerChatted(...)` return false → message dropped.
- Active checks use `activeCached(...)` so the hot path avoids the DB.

## 6. Data & events
`rock_punishments` + `rock_audit` (migration V005). Every action writes an audit
entry (also surfaced on the web dashboard `/api/v1/audit`).

## 7. Use-flow scenarios
1. **Temp-ban:** `/ban Griefer 7d griefing spawn` → kicked, blocked 7 days,
   audited. `/rock history Griefer` shows it ACTIVE.
2. **Mute & lift:** `/mute Spammer 1h` → chat dropped; `/unmute Spammer`.

## 8. Status — public-readiness gaps
- `/kick`, IP bans, tempban presets, customizable ban/mute screens.
- Tab-completion + clickable history/revoke (Command-Framework-v2).
- Moderation notes, staff-only alerts, web moderation panel actions.

## 9. API
```java
PunishmentService p = registry.require(PunishmentService.class);
p.punish(PunishmentType.BAN, targetId, issuer, reason, duration);
p.revoke(punishmentId, byUuid); p.history(targetId); p.activeCached(targetId, type);
```

## Roadmap / upgrade log
- _2026-06-14 · v2.0.0-insiders · documented baseline._
