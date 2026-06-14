# Command Framework v2 — CLI polish (design)

> Goal (Owner): make every command **public-ready** — tab-completion,
> clickable/interactive output, well-formatted results, and full per-command +
> per-argument help. Today commands are path + raw-args + plain text with no
> completion and no real help. This is the design to fix that.

> Status: **design — for review before build.** Backward-compatible: existing
> modules keep working while they migrate.

---

## 1. The four gaps → the four pieces

| Gap today | v2 piece |
|---|---|
| No tab-completion (adapter uses one greedy string arg) | **Typed argument model + brigadier tree from the spec** |
| Plain-text output, nothing clickable | **`RockMessage` rich-text model** (color/style/click/hover) |
| Inconsistent, unformatted output | **Formatting helpers** (headers, k/v, lists, pagination, theme) |
| No real help; bare `USAGE_ERROR` | **Help renderer** from arg metadata (`/rock help [path]`) |

All four live behind **rock-api contracts** so loaders/modules stay decoupled;
the renderer that turns `RockMessage` into a Minecraft `Component` is the only
loader-specific part (and is identical Fabric/NeoForge — `Component` is vanilla).

---

## 2. `RockMessage` — rich, clickable output (rock-api)

A tiny zero-dep component tree (not Minecraft's — ours, rendered at the seam):

```java
RockMessage.of("Claim ").color(GRAY)
    .append(RockMessage.of(claim.name()).color(GOLD).bold())
    .append(" ").append(
        RockMessage.button("[teleport]").color(AQUA)
            .runCommand("/rock claims tp " + claim.id())
            .hover("Teleport to this claim"))
    .append(RockMessage.button("[trust]").color(GREEN)
            .suggestCommand("/rock claims trust "));
```
- Styles: color (named + hex), bold/italic/underline.
- Click actions: `runCommand` · `suggestCommand` · `copyToClipboard` · `openUrl`.
- Hover: tooltip text.
- `CommandSender` gains `sendMessage(RockMessage)` (keeps `sendMessage(String)`).
- Loader adapters add a `RockMessage → net.minecraft.network.chat.Component`
  renderer (one class per loader; shared logic).

## 3. Typed arguments + tab-completion (rock-api + adapters)

`CommandSpec` gains an **argument list** (backward-compatible: empty = today's
behaviour). Each argument carries a type, requiredness, description, and an
optional suggestion provider:

```java
CommandSpec.builder("claims", "trust")
    .description("Trust a player in your claim")
    .permission("rock.claims.trust")
    .arg(Arg.player("player").describe("who to trust"))
    .arg(Arg.enumOf("role", ClaimRole.class).describe("trust level"))
    .executes(ctx -> { ... });
```
- `Arg` types: `word`, `player` (online-name suggestions), `integer(min,max)`,
  `enumOf`, `greedy`, plus custom `suggests((sender, partial) -> List<String>)`
  (claim names, group names, warps, …).
- The loader adapter builds a **real brigadier tree** from
  `CommandService.registered()`: literal nodes for the path, argument nodes with
  `SuggestionProvider`s delegating to the spec → **tab-completion for free**, on
  `/rock` and every alias. Replaces the current single-greedy-string root.
- `CommandContext` gains typed getters: `ctx.player("player")`,
  `ctx.enumArg("role", ClaimRole.class)`, `ctx.intArg("n")` — no more manual
  `args.get(0)` + `valueOf` + try/catch in every module.

## 4. Help system (rock-core)

- `/rock help` — grouped, paginated, clickable index (click a command to run/fill).
- `/rock help <path>` and bare `/rock <group>` (e.g. `/rock claims`) — usage
  built from arg metadata: `Usage: /rock claims trust <player> <role>` with each
  arg's description, the permission, and clickable examples.
- Auto-generated from the spec; no per-module help strings to maintain.
- Replaces bare `USAGE_ERROR` with a real, formatted usage message.

## 5. Formatting helpers (rock-core)

A `Messages` helper for a consistent house style so every module's output
matches: `header(title)`, `kv(key, value)`, `list(items)`, `success/error/info`,
`paginate(items, page, perPageCommand)`. Colors/symbols defined once.

## 6. Rollout plan (incremental, always-green)

1. **rock-api:** `RockMessage`, `Arg`/argument model on `CommandSpec` (builder),
   typed `CommandContext` getters, `CommandSender.sendMessage(RockMessage)`.
2. **rock-core:** dispatch uses arg metadata (typed parse + auto usage), help
   renderer, `Messages` helpers. Existing string output still works.
3. **Adapters (Fabric + NeoForge):** brigadier tree from the spec (with
   suggestions) + `RockMessage`→`Component` renderer. On-server tab-complete +
   clickable verified by the harness.
4. **Migrate modules**, `rock-claims` first as the reference (clickable
   `list`/`info`/`trust`, completions, help), then the rest.
5. Per-module doc (DOCS/modules/*) updated as each is polished.

## 7. Backward compatibility

- `CommandSpec` keeps its current constructor; the builder + args are additive.
- `sendMessage(String)` stays; `RockMessage` is opt-in.
- A module not yet migrated still registers and runs exactly as today — just
  without completion/clickability until it adopts v2.
