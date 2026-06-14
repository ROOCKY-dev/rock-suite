# rock-permissions

> The permission engine — players & groups, wildcards, contexts, temporary
> grants, and meta/options. LuckPerms-class depth; the authority every other
> module's permission checks resolve through.

- **Module id:** `rock-permissions` · **Depends:** rock-core, rock-data
- **Service:** `dev.rock.api.services.PermissionService`
- **Status:** functional & deep; **not yet public-polished** (see §8).

## 1. What it does
Resolves whether a player has a permission node, combining player grants, group
memberships, contexts, temporary grants, and wildcards into a single decision —
cached for the hot path, rebuildable via `reload()`.

## 2. Concepts
- **State** (`PermissionState`): `ALLOW` · `DENY` · `UNSET`. A player `DENY`
  overrides a group `ALLOW`.
- **Groups** (`RockGroup`): named, with a **priority** (lower = stronger); ties
  break **alphabetically** (DMS rule). Players can be in many groups.
- **Wildcards:** `rock.claims.*` grants every `rock.claims.<x>`.
- **Contexts** (`ContextSet`, e.g. `world=nether`): a grant can be scoped; a more
  specific context wins over global. (API-level today; not yet in commands.)
- **Temporary:** `grantTemporary(node, duration)` — expires by evaluation, no
  sweep needed.
- **Meta/options:** string options (`prefix`, `suffix`) and numeric
  (`rock.essentials.homes.max`); resolved player-first, then by group order.

## 3. Commands
All gated by `rock.admin.permissions`.

| Command | Behaviour |
|---|---|
| `/rock perms grant <player> <node>` | Grant a node (ALLOW). |
| `/rock perms deny <player> <node>` | Deny a node (DENY). |
| `/rock perms unset <player> <node>` | Remove a node (UNSET). |
| `/rock perms check <player> <node>` | Show the evaluated state. |
| `/rock perms group create <name> <priority>` | Create a group. |
| `/rock perms group grant <group> <node>` | Grant a node to a group. |
| `/rock perms group assign <player> <group>` | Add a player to a group. |
| `/rock perms setoption <player> <key> <value>` | Set player meta/option. |

**[QoL gaps]:** no tab-completion (players/groups/nodes); **no listing commands**
(`perms list <player>`, `perms group list`, `group unassign`, `group setperm
remove`); contexts & temporary grants are API-only (no command flags yet);
plain text (no clickable node toggles — that's the in-game permission editor,
rock-client Tier 2). *Internal wart:* group-by-name resolves by casting
`DefaultPermissionService` — a `PermissionService.findGroup(name)` belongs on the
interface.

## 4. Permissions
Admin surface: `rock.admin.permissions`. (Players don't self-manage perms.)

## 5. Data
`rock_permissions`, `rock_groups`, `rock_group_permissions`, `rock_player_groups`
(V002), + contexts & options columns (V009).

## 6. Loader integration (K2)
Permission **providers** bridge ROCK permissions to the loader's permission API
(e.g. fabric-permissions-api), so other mods see ROCK's decisions. The op-bypass
in the adapter is the only non-ROCK fallback.

## 7. Use-flow scenarios
1. **Rank setup:** `group create Member 100` → `group grant Member rock.essentials.*`
   → `group assign Alice Member`.
2. **Targeted deny:** `deny Bob rock.economy.pay` overrides his group's allow.
3. **VIP event (API):** `grantTemporary(uuid, "rock.event.vip", 2h)`.

## 8. Status — public-readiness gaps
- Listing/inspection commands; `group unassign`; remove group node.
- Context & temporary **command** flags (API exists).
- Group inheritance (group→group); prefix/suffix chat rendering.
- `findGroup(name)` on the service interface (remove the cast).
- Tab-completion; in-game permission editor (rock-client Tier 2) + web editor.

## 9. API
```java
PermissionService p = registry.require(PermissionService.class);
p.has(uuid, node); p.check(uuid, node);            // + context overloads
p.grant/deny/unset(uuid, node); p.grantTemporary(uuid, node, duration);
p.createGroup(name, prio); p.grantGroup(gid, node); p.assignGroup(uuid, gid);
p.groupsOf(uuid); p.setPlayerOption/setGroupOption; p.option/intOption; p.reload();
```

## Roadmap / upgrade log
- _2026-06-14 · v2.0.0-insiders · documented baseline._
