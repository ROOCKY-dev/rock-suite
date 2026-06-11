package dev.rock.api.data;

import dev.rock.api.service.RockService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The single data-access contract for all modules (TRS §5, AVD §10).
 *
 * <p>Every method is asynchronous by signature: blocking the Minecraft tick
 * thread on the database is impossible to express through this interface
 * (Architectural Review D-5). Implementations execute on virtual threads.
 *
 * <p>SQL uses named parameters: {@code SELECT * FROM players WHERE id = :id}.
 */
public interface DataService extends RockService {

    <T> CompletableFuture<List<T>> query(String sql, Map<String, Object> params, RowMapper<T> mapper);

    <T> CompletableFuture<Optional<T>> queryOne(String sql, Map<String, Object> params, RowMapper<T> mapper);

    /** Returns the number of affected rows. */
    CompletableFuture<Integer> update(String sql, Map<String, Object> params);

    /** Executes the same statement for each parameter map, in one batch. */
    CompletableFuture<int[]> batch(String sql, List<Map<String, Object>> batchParams);

    /** Runs the work atomically; rolls back on any exception. */
    <T> CompletableFuture<T> inTransaction(TransactionalWork<T> work);
}
