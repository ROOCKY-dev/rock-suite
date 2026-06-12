package dev.rock.api.services;

import dev.rock.api.domain.RockItemLogEntry;
import dev.rock.api.domain.RockWorldLogEntry;
import dev.rock.api.service.RockService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Block logging & rollback — the module no server admin lives without.
 * Recording happens automatically from BlockChangeEvent; this contract is the
 * query/repair surface. All operations are async (TRS §3).
 */
public interface WorldLogService extends RockService {

    CompletableFuture<List<RockWorldLogEntry>> query(LogQuery query);

    /**
     * Reverts matching, not-yet-rolled-back changes (newest first) by applying
     * each entry's {@code blockBefore} through the {@link dev.rock.api.world.WorldMutator}.
     * Returns the number of entries reverted; entries stay in the log flagged
     * {@code rolledBack} so they can be restored.
     */
    CompletableFuture<Integer> rollback(LogQuery query);

    /** Re-applies previously rolled-back changes (oldest first). Inverse of rollback. */
    CompletableFuture<Integer> restore(LogQuery query);

    /** Forces any buffered log entries to be written (used on shutdown/tests). */
    CompletableFuture<Void> flush();

    // --- P2 (since 1.4) ------------------------------------------------------

    /** Container item movements matching the filter (theft tracking). */
    CompletableFuture<List<RockItemLogEntry>> queryItems(LogQuery query);

    /** Inspector: full block history at one position, newest first. */
    default CompletableFuture<List<RockWorldLogEntry>> inspect(UUID worldId, int x, int y, int z, int limit) {
        return query(LogQuery.builder().world(worldId).around(x, y, z, 0).limit(limit).build());
    }

    /** Dry run of {@link #rollback}: what would be reverted, without applying. */
    CompletableFuture<RollbackPreview> previewRollback(LogQuery query);
}
