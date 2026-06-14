# rock-backup

> Scheduled world + database **backups** with retention (TRS §14). Snapshots run
> off-tick on a timer; restore is a deliberate, restart-time operation.

- **Module id:** `rock-backup` · **Depends:** rock-core, rock-data
- **Service:** `dev.rock.api.services.BackupService`
- **Status:** functional (scheduled); **no command surface yet** (§5).

## 1. What it does
On a configurable interval, snapshots the ROCK data (and world data) and prunes
old snapshots to a retention count. Scheduling uses the platform `RockScheduler`
(off the tick thread). Restore over a live server requires a restart — never a
hot swap (data integrity).

## 2. Concepts
- `BackupService.BackupInfo`: id, created, sizeBytes.
- `createBackup(label)` · `list()` · `prune(keep)`.

## 3. Configuration (`rock-backup.toml`)
```toml
[backup]
interval-minutes = 360   # 0 disables the schedule
retention = 12           # keep the newest N
```

## 4. Commands
**None yet.** Backups run on the schedule; there's no in-game trigger.

## 5. Status — public-readiness gaps
- **Commands:** `/rock backup now [label]`, `list`, `info`, `restore <id>`
  (with a confirm + restart notice), `prune`.
- Configurable backup target (path), what's included (world selection), and
  **off-site/S3** destinations; compression level.
- Pre-restore safety snapshot; integrity verification; backup-on-stop.
- Web dashboard backup panel (trigger/list/download).

## 6. API
```java
BackupService b = registry.require(BackupService.class);
b.createBackup("pre-update"); b.list(); b.prune(12);
```

## Roadmap / upgrade log
- _2026-06-14 · v2.0.0-insiders · documented baseline._
