package dev.rock.logging;

import dev.rock.api.domain.RockWorldLogEntry;
import dev.rock.api.services.LogQuery;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Storage abstraction for the world log; backed by DataService in production. */
public interface WorldLogRepository {

    CompletableFuture<Void> insertBatch(List<RockWorldLogEntry> entries);

    /**
     * @param rolledBack filter on the rolled-back flag; null = any
     * @param oldestFirst true → ts ascending (restore order), false → descending
     */
    CompletableFuture<List<RockWorldLogEntry>> find(LogQuery query, Boolean rolledBack, boolean oldestFirst);

    CompletableFuture<Void> markRolledBack(List<RockWorldLogEntry> entries, boolean rolledBack);
}
