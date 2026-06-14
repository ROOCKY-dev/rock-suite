# rock-claims

> Land protection: players claim chunks and control who can build, open
> containers, and interact inside them. Template module doc — the structure here
> is reused for every module; **Status** + **Roadmap / upgrade log** are the
> living sections you update as features land.

- **Module id:** `rock-claims` · **Depends:** rock-core, rock-data, rock-permissions
- **Service:** `dev.rock.api.services.ClaimService`
- **Status:** functional; **not yet public-polished** (see §9).

---

## 1. What it does (purpose)

Chunk-based land claiming with a trust system and per-claim protection flags.
The server-authoritative source of truth for "who may do what, where" — enforced
on the tick path via the world-event layer (rock-logging/world events feed it).

## 2. Concepts

**Claim types** (`ClaimType`): `PLAYER` · `TOWN` · `FACTION` · `NATION` ·
`ADMIN`. Today only `PLAYER` is created via commands; the others are modelled
for rock-teams/towns growth.

**Trust roles** (`ClaimRole`, ascending power): `ACCESS` → `CONTAINER` → `BUILD`
→ `MANAGER`. `atLeast(role)` is the gate check (a MANAGER passes a BUILD check).
- ACCESS — use doors/buttons/etc.
- CONTAINER — + open chests/containers.
- BUILD — + place/break blocks.
- MANAGER — + trust/untrust others, set flags, abandon.

**Protection flags** (`ClaimFlag`, all default **deny/false**):
`PVP` · `EXPLOSIONS` · `MOB_GRIEFING` · `FAKE_PLAYERS` · `FIRE_SPREAD`.

**Bounds:** `ChunkBounds` — a set of chunk coordinates in one world. (Block-box
and sub-claim bounds are modelled via `ClaimBounds`/`BoundsType` for later.)

## 3. Commands

All under `/rock claims …` (see also planned aliases). **[QoL gap]** marks
behaviour the Command-Framework-v2 pass (DOCS/COMMAND_FRAMEWORK.md) will fix.

| Command | Permission | Args | Behaviour |
|---|---|---|---|
| `/rock claims claim [name]` | `rock.claims.create` | optional name | Claims the chunk you stand in (default name "<you>'s claim"). |
| `/rock claims info` | `rock.claims.info` | — | Shows the claim you're standing in: name, owner, type, members. |
| `/rock claims trust <player> <role>` | `rock.claims.trust` | player, role | MANAGER-only. Trusts `<player>` at `ACCESS\|CONTAINER\|BUILD\|MANAGER`. |
| `/rock claims untrust <player>` | `rock.claims.trust` | player | MANAGER-only. Removes a player's trust. |
| `/rock claims abandon` | `rock.claims.create` | — | MANAGER-only. Deletes the claim you stand in. |

**[QoL gaps] (current):** no tab-completion for subcommands/roles/player names;
plain-text output (no clickable `[teleport]`/`[trust]` actions); errors are bare
`USAGE_ERROR` with no per-argument help; no `/rock claims list`, `setflag`,
`flags`, or `trustlist`; no claim map/visualisation (that's rock-client Tier 1).

## 4. Permissions

| Node | Grants |
|---|---|
| `rock.claims.create` | claim + abandon |
| `rock.claims.info` | view claim info |
| `rock.claims.trust` | manage trust on claims you MANAGE |
| `rock.claims.*` | all of the above |

Trust is enforced **per claim** on top of the permission (a player with the
permission still needs MANAGER on the specific claim to trust/abandon).

## 5. Protection behaviour (what's enforced)

`ClaimProtectionListener` subscribes to `BlockChangeEvent` and
`PlayerInteractEvent` (priority EARLY) and cancels when:
- a player below the required role acts (BUILD for block change, CONTAINER/ACCESS
  for interactions);
- mob griefing in a claim without `MOB_GRIEFING`;
- a fake player (machine/automation) acts without `FAKE_PLAYERS`;
- (PVP / EXPLOSIONS / FIRE_SPREAD flags gate their respective events).
Lookups use `ClaimService.claimAtCached(...)` (an in-memory `ClaimIndex`) so the
hot path never hits the DB.

## 6. Data model

`rock_claims` (V003) — id, display name, owner ref, type, bounds, timestamps,
soft-delete. `rock_claim_members` (V008) — claim_id, player_id, role. Owner is a
serialized `OwnerReference` (`PLAYER:uuid`, `TEAM:…`, …).

## 7. Events

- **Consumes:** `BlockChangeEvent`, `PlayerInteractEvent` (cancels on violation).
- **Projects (rock-protocol):** `claim.entered` (boundary toast),
  `claim.list.item`/`claim.list.end` (the `claims.list` intent) — for
  rock-client / rock-web.

## 8. Use-flow scenarios

1. **Claim & protect:** stand in a chunk → `/rock claims claim Base` → block
   changes by others are now cancelled (BUILD required); chests are CONTAINER-only.
2. **Invite a friend:** `/rock claims trust Alice BUILD` → Alice can build but
   not manage. `… trust Alice MANAGER` to let her run the claim.
3. **Leave:** `/rock claims abandon` (MANAGER) → claim removed, chunk open again.

## 9. Status — public-readiness gaps (work to do)

- **Commands:** tab-completion, clickable/formatted output, real per-arg help
  (Command-Framework-v2). Add `list`, `flags`, `setflag`, `trustlist`.
- **Claiming UX:** multi-chunk / drag selection, claim resize, sub-claims,
  per-claim flag editing in-game; claim limits per player (via permission meta
  `rock.claims.max`).
- **Visualisation:** in-world boundary particles + claim map (rock-client Tier 1).
- **Types:** wire TOWN/FACTION/NATION to rock-teams.

## 10. API (for module/integration devs)

```java
ClaimService claims = registry.require(ClaimService.class);
claims.create(name, new PlayerOwner(uuid), ClaimType.PLAYER, new ChunkBounds(world, chunks));
claims.claimAt(worldId, x, y, z);            // async lookup
claims.claimAtCached(worldId, x, y, z);      // hot-path cached lookup
claims.effectiveRole(claim, playerId);       // resolve a player's role
claims.flag(claim, ClaimFlag.PVP);           // read a flag
```

---

## Roadmap / upgrade log

> Append an entry per shipped change (date · version · what · why). This is the
> running record the Owner maintains as the module evolves.

- _2026-06-14 · v2.0.0-insiders · documented baseline (this file)._
