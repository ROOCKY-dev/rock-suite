# rock-migrate (RMG)

> One-shot **importers** that pull data from incumbent plugins/mods into ROCK, so
> admins can switch without losing permissions, balances, claims, or history.

- **Module id:** `rock-migrate` · **Depends:** rock-core, rock-data, rock-permissions
- **Status:** functional (2 importers); **early — many sources still to add** (§5).

## 1. What it does
Registers named **importers**; an admin runs one against a source path and gets a
report (counts imported + warnings). Importers write through the normal ROCK
services, so imported data is indistinguishable from native data.

## 2. Command
| Command | Permission | Behaviour |
|---|---|---|
| `/rock migrate` | `rock.admin.migrate` | Lists available importers. |
| `/rock migrate <importer> <path>` | `rock.admin.migrate` | Runs the importer; prints imported count + up to 10 warnings (rest in log). |

**[QoL gaps]:** no tab-completion (importer id / file path); no dry-run/preview;
no progress for large sources; no per-record conflict policy (skip/overwrite).

## 3. Importers (current)
| id | Source | Imports |
|---|---|---|
| `luckperms` | LuckPerms export | players, groups, nodes |
| `essentials` | EssentialsX userdata | balances |

Each implements `RmgImporter` (`id`, `description`, `run(Path) → ImportReport`),
so adding a source is a self-contained class.

## 4. Use-flow
`/rock migrate luckperms /path/to/luckperms-export.json` → "Import complete: N"
+ warnings. Then `/rock perms check …` to verify.

## 5. Status — public-readiness gaps (the backlog is the point)
Planned importers (MODULE_ROADMAP P2): **GriefPrevention / Towny** (claims),
**CoreProtect / Ledger** (block history), **FTB Chunks / Ranks / Teams**,
**Flan**, **OPAC** (claims), **Vault** balances. Plus: dry-run preview, progress
output, conflict policy, and a guided "migrate everything" wizard.

## 6. API (add an importer)
```java
public final class MyImporter implements RmgImporter {
    public String id() { return "myplugin"; }
    public String description() { return "Imports MyPlugin data"; }
    public CompletableFuture<ImportReport> run(Path source) { /* write via ROCK services */ }
}
// register it in MigrateCommands' constructor wiring.
```

## Roadmap / upgrade log
- _2026-06-14 · v2.0.0-insiders · documented baseline._
