package dev.rock.backup;

import dev.rock.api.config.RockConfig;

/**
 * Parsed rock-backup.toml.
 *
 * @param intervalMinutes 0 = scheduled backups disabled
 * @param retention       how many archives prune() keeps
 */
public record BackupSettings(long intervalMinutes, int retention) {

    public static BackupSettings fromConfig(RockConfig config) {
        return new BackupSettings(
                config.getLong("backup.interval-minutes", 360),
                config.getInt("backup.retention", 12));
    }
}
