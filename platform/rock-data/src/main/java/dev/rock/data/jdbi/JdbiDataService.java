package dev.rock.data.jdbi;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.data.DataService;
import dev.rock.api.data.RowMapper;
import dev.rock.api.data.TransactionContext;
import dev.rock.api.data.TransactionalWork;
import dev.rock.api.lifecycle.LifecycleAware;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Update;

/**
 * JDBI3-backed DataService (Architectural Review D-5). Every public method
 * hops to a virtual-thread executor, so the game thread can never block on
 * the database (TRS §3). JDBI types never cross this boundary.
 */
@RockInternal
@Singleton
public final class JdbiDataService implements DataService, LifecycleAware {

    private final Jdbi jdbi;
    private final ExecutorService executor;

    @Inject
    public JdbiDataService(Jdbi jdbi) {
        this.jdbi = jdbi;
        this.executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("rock-data-", 0).factory());
    }

    private <T> CompletableFuture<T> async(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, executor);
    }

    @Override
    public <T> CompletableFuture<List<T>> query(String sql, Map<String, Object> params, RowMapper<T> mapper) {
        return async(() -> jdbi.withHandle(handle -> runQuery(handle, sql, params, mapper)));
    }

    @Override
    public <T> CompletableFuture<Optional<T>> queryOne(String sql, Map<String, Object> params, RowMapper<T> mapper) {
        return async(() -> jdbi.withHandle(handle -> {
            List<T> results = runQuery(handle, sql, params, mapper);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        }));
    }

    @Override
    public CompletableFuture<Integer> update(String sql, Map<String, Object> params) {
        return async(() -> jdbi.withHandle(handle -> runUpdate(handle, sql, params)));
    }

    @Override
    public CompletableFuture<int[]> batch(String sql, List<Map<String, Object>> batchParams) {
        return async(() -> jdbi.withHandle(handle -> {
            var prepared = handle.prepareBatch(sql);
            for (Map<String, Object> params : batchParams) {
                prepared.bindMap(params).add();
            }
            return prepared.execute();
        }));
    }

    @Override
    public <T> CompletableFuture<T> inTransaction(TransactionalWork<T> work) {
        return async(() -> jdbi.inTransaction(handle -> {
            try {
                return work.execute(new HandleTransactionContext(handle));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new DataAccessException("Transactional work failed", e);
            }
        }));
    }

    private static <T> List<T> runQuery(Handle handle, String sql, Map<String, Object> params, RowMapper<T> mapper) {
        return handle.createQuery(sql)
                .bindMap(params)
                .map((rs, ctx) -> mapper.map(new ResultSetRowView(rs)))
                .list();
    }

    private static int runUpdate(Handle handle, String sql, Map<String, Object> params) {
        Update update = handle.createUpdate(sql);
        return update.bindMap(params).execute();
    }

    /** Synchronous mirror used inside transactions — already off the game thread. */
    private record HandleTransactionContext(Handle handle) implements TransactionContext {

        @Override
        public <T> List<T> query(String sql, Map<String, Object> params, RowMapper<T> mapper) {
            return runQuery(handle, sql, params, mapper);
        }

        @Override
        public <T> Optional<T> queryOne(String sql, Map<String, Object> params, RowMapper<T> mapper) {
            List<T> results = runQuery(handle, sql, params, mapper);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        }

        @Override
        public int update(String sql, Map<String, Object> params) {
            return runUpdate(handle, sql, params);
        }
    }

    @Override
    public void onEnable() {
        // Connection pool and migrations are owned by RockDataModule/DataMigrator.
    }

    @Override
    public void onDisable() {
        executor.shutdown();
    }
}
