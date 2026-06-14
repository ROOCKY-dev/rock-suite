# rock-essentials

> Player quality-of-life: **homes, warps, and teleport requests (TPA)**.
> Persistence always works; physical teleport uses a loader-provided teleporter
> and degrades to an informative message when absent.

- **Module id:** `rock-essentials` · **Depends:** rock-core, rock-data, rock-permissions
- **Service:** `dev.rock.api.services.EssentialsService` · **Loader SPI:** `dev.rock.api.world.PlayerTeleporter`
- **Status:** functional; **not yet public-polished** (see §8).

## 1. What it does
Saves named homes per player and server-wide warps (as `RockLocation`s), and
brokers TPA requests between players. The actual teleport is performed by the
loader's `PlayerTeleporter` (resolved via ServiceRegistry); without it, ROCK
still records/answers but tells the player teleport needs loader support.

## 2. Commands
| Command | Permission | Behaviour |
|---|---|---|
| `/rock sethome [name]` (alias `/sethome`) | `rock.essentials.home` | Saves a home (default name `home`). |
| `/rock home [name]` (alias `/home`) | `rock.essentials.home` | Teleports to a home; lists homes if name unknown. |
| `/rock homes` | `rock.essentials.home` | Lists your homes. |
| `/rock setwarp <name>` | `rock.essentials.admin.setwarp` | Creates a server warp at your position. |
| `/rock warp [name]` (alias `/warp`) | `rock.essentials.warp` | Teleports to a warp; lists warps if none given. |
| `/rock tpa <player>` (alias `/tpa`) | `rock.essentials.tpa` | Requests teleport to a player. |
| `/rock tpaccept` | `rock.essentials.tpa` | Accepts the pending request. |
| `/rock tpdeny` | `rock.essentials.tpa` | Denies the pending request. |

**[QoL gaps]:** no tab-completion (home/warp/player names); **plain-text TPA**
(no clickable Accept/Deny — the rock-client toast covers this only for client
users); no `delhome`/`delwarp`, `/spawn`, `/back`, `/tphere`, TPA expiry timer,
per-rank **home limits** (planned via permission meta `rock.essentials.homes.max`).

## 3. Permissions
`rock.essentials.home` · `.warp` · `.tpa` · `rock.essentials.admin.setwarp` ·
`rock.essentials.*`.

## 4. Data
`rock_homes` + `rock_warps` (migration V011). Locations store world id + x/y/z +
yaw/pitch.

## 5. Loader integration
`PlayerTeleporter` (rock-api SPI) is provided by the loader adapter (locate +
teleport). Essentials checks for it at call time — graceful degradation keeps the
module loader-agnostic.

## 6. Use-flow scenarios
1. **Home:** `/sethome base` → later `/home base` teleports back.
2. **Warp:** admin `/setwarp shop` → players `/warp shop`.
3. **TPA:** `/tpa Alice` → Alice `/tpaccept` → requester is teleported to Alice.

## 7. Status — public-readiness gaps
- `delhome`/`delwarp`, `/spawn`, `/back`, `/tphere`/`/tpahere`, TPA timeout.
- Per-rank home limits (permission meta), warp categories/permissions per warp.
- Tab-completion + clickable home/warp lists + TPA buttons (Command-Framework-v2).

## 8. API
```java
EssentialsService e = registry.require(EssentialsService.class);
e.setHome(uuid, name, location); e.home(uuid, name); e.homes(uuid);
e.setWarp(name, location, byUuid); e.warp(name); e.warps();
e.tpa(from, to); e.tpaccept(target); e.tpdeny(target);
```

## Roadmap / upgrade log
- _2026-06-14 · v2.0.0-insiders · documented baseline._
