package dev.rock.api.data;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Synchronous data access inside a database transaction. The whole transaction
 * already runs on a platform worker (virtual) thread, so these calls may block
 * that worker — never the game thread.
 */
public interface TransactionContext {

    <T> List<T> query(String sql, Map<String, Object> params, RowMapper<T> mapper);

    <T> Optional<T> queryOne(String sql, Map<String, Object> params, RowMapper<T> mapper);

    int update(String sql, Map<String, Object> params);
}
