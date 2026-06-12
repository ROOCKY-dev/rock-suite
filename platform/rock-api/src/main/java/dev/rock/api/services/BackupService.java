package dev.rock.api.services;

import dev.rock.api.service.RockService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Built-in backup scheduling and restore (TRS §14). Backups snapshot the ROCK
 * data directory (database, config) into timestamped zip archives with a
 * retention policy. Restore unpacks into a staging directory — applying a
 * restore over a live server requires a restart, never a hot swap.
 */
public interface BackupService extends RockService {

    record BackupInfo(String id, Instant created, long sizeBytes) {
        public BackupInfo {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(created, "created");
        }
    }

    /** Creates a backup now; label becomes part of the archive name. */
    CompletableFuture<BackupInfo> createBackup(String label);

    /** All backups, newest first. */
    CompletableFuture<List<BackupInfo>> list();

    /** Deletes oldest backups beyond {@code keep}; returns how many were removed. */
    CompletableFuture<Integer> prune(int keep);

    /**
     * Unpacks a backup into a staging directory and returns its path. The
     * operator swaps it in while the server is stopped (no manual SQL — TRS §14).
     */
    CompletableFuture<Path> restoreToStaging(String backupId);
}
