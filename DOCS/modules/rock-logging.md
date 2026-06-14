# rock-logging

> Block/world logging with **rollback & restore** — the grief-repair surface
> (CoreProtect-class). The "wedge" module: the headline reason to install ROCK.

- **Module id:** `rock-logging` · **Depends:** rock-core, rock-data
- **Service:** `dev.rock.api.services.WorldLogService`
- **Status:** functional; **not yet public-polished** (see §7).

## 1. What it does
Records every block change (break/place, and environment changes) with actor,
position, before/after block, and time — then lets an admin query, preview, roll
back, and restore changes around them. Writes are **async/off-tick** (a batching
`LogConsumer`) so logging never costs TPS.

## 2. Concepts
- `RockWorldLogEntry`: action, blockBefore→blockAfter, x/y/z, actor (null =
  environment), timestamp.
- `LogQuery` (builder): `world`, `around(x,y,z,radius)`, `limit`.
- `RollbackPreview`: total `entries()` + `byAction()` counts — a dry run.

## 3. Commands
All gated by `rock.logging.admin`, scoped around the sender's position.

| Command | Behaviour |
|---|---|
| `/rock log near [radius]` | Recent changes nearby (default radius 10), first 10 shown. |
| `/rock log preview [radius]` | Dry-run: how many changes a rollback would revert, by action. |
| `/rock log rollback [radius]` | Reverts changes in radius. |
| `/rock log restore [radius]` | Re-applies a previous rollback. |

**[QoL gaps]:** no **inspector mode** (punch a block to see its history — that's
rock-client Tier 1 / admin overlay); no filters (by player, action, or time
window like `t:1h`); no pagination/clickable results; no lookup of a named
player's edits server-wide; container/item-transaction logging exists in data
(`rock_item_log`) but has no command surface yet.

## 4. Permissions
`rock.logging.admin` (the whole grief-repair surface).

## 5. Data
`rock_world_log` (V007) + a sequence (V013) for ordering; `rock_item_log` (V012)
for container transactions.

## 6. Use-flow scenarios
1. **Investigate:** stand at the grief → `/rock log near 15` → see who did what.
2. **Repair:** `/rock log preview 15` (confirm scope) → `/rock log rollback 15`
   → if wrong, `/rock log restore 15`.

## 7. Status — public-readiness gaps
- Inspector mode + ghost-block rollback **preview render** (rock-client Tier 1).
- Filters: `by player`, `action`, `time window`; server-wide player lookup.
- Container/item log command surface; pagination + clickable entries.
- Rollback by time/selection box (not just radius).

## 8. API
```java
WorldLogService log = registry.require(WorldLogService.class);
var q = LogQuery.builder().world(w).around(x,y,z,15).limit(5000).build();
log.query(q); log.previewRollback(q); log.rollback(q); log.restore(q);
```

## Roadmap / upgrade log
- _2026-06-14 · v2.0.0-insiders · documented baseline._
