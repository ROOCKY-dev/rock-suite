# rock-teams

> Teams/parties/guilds as a **first-class identity** — a roster with roles that
> other modules build on (claims map team roles onto trust; economy/chat can
> scope to a team).

- **Module id:** `rock-teams` · **Depends:** rock-core, rock-data
- **Service:** `dev.rock.api.services.TeamService`
- **Status:** **service/data only — no command surface yet** (the biggest gap, §5).

## 1. What it does
Stores teams and their members with roles, exposing cached reads for the tick
path so claims/other modules can ask "is X in team T, and at what role?" without
a DB hit. It's the identity layer the TOWN/FACTION claim types will use.

## 2. Concepts
- `RockTeam`: id, name, leader, members.
- `TeamRole` (membership rank) — maps onto claim trust via rock-claims.
- Cached reads: `teamOfCached`, `roleOfCached` (tick-thread safe).

## 3. Commands
**None yet.** This is the headline gap — there is no `/rock team …` surface, so
teams are only creatable via the API today.

## 4. Data
`rock_teams` + `rock_team_members` (migration V010).

## 5. Status — public-readiness gaps
- **Command surface** (`/rock team create|invite|join|leave|kick|promote|info|
  list`, `/rock team chat`), with invites + confirmations.
- Team homes/warps (shared, via rock-essentials), team economy accounts.
- Wire TOWN/FACTION/NATION claim types to teams (claim-by-team, team trust).
- Team panel + shared waypoints in rock-client (RFC-001 Tier 1).
- Parties (transient) vs guilds (persistent) distinction.

## 6. API
```java
TeamService t = registry.require(TeamService.class);
t.create(name, leaderUuid); t.addMember(teamId, uuid, role); t.removeMember(...);
t.membersOf(teamId); t.findByName(name); t.disband(teamId);
t.roleOfCached(teamId, uuid);   // hot-path
```

## Roadmap / upgrade log
- _2026-06-14 · v2.0.0-insiders · documented baseline._
