package dev.rock.logging;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.data.DataService;
import dev.rock.api.data.RowMapper;
import dev.rock.api.domain.RockWorldLogEntry;
import dev.rock.api.events.world.BlockChangeType;
import dev.rock.api.services.LogQuery;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** World-log storage on the platform DataService — zero JDBC/JDBI imports (TRS §5). */
@RockInternal
@Singleton
public final class DataServiceWorldLogRepository implements WorldLogRepository {

    private static final RowMapper<RockWorldLogEntry> ENTRY_MAPPER = row -> new RockWorldLogEntry(
            row.getUuid("id"),
            row.getUuid("actor"),
            row.getInt("fake_actor") != 0,
            row.getUuid("world_id"),
            row.getInt("x"),
            row.getInt("y"),
            row.getInt("z"),
            BlockChangeType.valueOf(row.getString("action")),
            row.getString("block_before"),
            row.getString("block_after"),
            row.getInstant("ts"),
            row.getInt("rolled_back") != 0);

    private static final String INSERT = """
            INSERT INTO rock_world_log
                (id, actor, fake_actor, world_id, x, y, z, action, block_before, block_after, ts, rolled_back)
            VALUES (:id, :actor, :fake, :world, :x, :y, :z, :action, :before, :after, :ts, :rb)
            """;

    private final DataService data;

    @Inject
    public DataServiceWorldLogRepository(DataService data) {
        this.data = data;
    }

    private static Map<String, Object> params(RockWorldLogEntry entry) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", entry.id().toString());
        params.put("actor", entry.actor() == null ? null : entry.actor().toString());
        params.put("fake", entry.fakeActor() ? 1 : 0);
        params.put("world", entry.worldId().toString());
        params.put("x", entry.x());
        params.put("y", entry.y());
        params.put("z", entry.z());
        params.put("action", entry.action().name());
        params.put("before", entry.blockBefore());
        params.put("after", entry.blockAfter());
        params.put("ts", entry.timestamp().toEpochMilli());
        params.put("rb", entry.rolledBack() ? 1 : 0);
        return params;
    }

    @Override
    public CompletableFuture<Void> insertBatch(List<RockWorldLogEntry> entries) {
        return data.batch(INSERT, entries.stream().map(DataServiceWorldLogRepository::params).toList())
                .thenApply(counts -> null);
    }

    @Override
    public CompletableFuture<List<RockWorldLogEntry>> find(LogQuery query, Boolean rolledBack, boolean oldestFirst) {
        StringBuilder sql = new StringBuilder("SELECT * FROM rock_world_log WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        if (query.worldId() != null) {
            sql.append(" AND world_id = :world");
            params.put("world", query.worldId().toString());
        }
        if (query.actor() != null) {
            sql.append(" AND actor = :actor");
            params.put("actor", query.actor().toString());
        }
        if (query.since() != null) {
            sql.append(" AND ts >= :since");
            params.put("since", query.since().toEpochMilli());
        }
        if (query.until() != null) {
            sql.append(" AND ts <= :until");
            params.put("until", query.until().toEpochMilli());
        }
        if (query.radius() != null) {
            sql.append(" AND x BETWEEN :x1 AND :x2 AND y BETWEEN :y1 AND :y2 AND z BETWEEN :z1 AND :z2");
            params.put("x1", query.centerX() - query.radius());
            params.put("x2", query.centerX() + query.radius());
            params.put("y1", query.centerY() - query.radius());
            params.put("y2", query.centerY() + query.radius());
            params.put("z1", query.centerZ() - query.radius());
            params.put("z2", query.centerZ() + query.radius());
        }
        if (query.action() != null) {
            sql.append(" AND action = :action");
            params.put("action", query.action().name());
        }
        if (rolledBack != null) {
            sql.append(" AND rolled_back = :rb");
            params.put("rb", rolledBack ? 1 : 0);
        }
        sql.append(" ORDER BY ts ").append(oldestFirst ? "ASC" : "DESC");
        sql.append(" LIMIT :limit");
        params.put("limit", query.limit());
        return data.query(sql.toString(), params, ENTRY_MAPPER);
    }

    @Override
    public CompletableFuture<Void> markRolledBack(List<RockWorldLogEntry> entries, boolean rolledBack) {
        List<Map<String, Object>> batch = new ArrayList<>();
        for (RockWorldLogEntry entry : entries) {
            batch.add(Map.of("id", entry.id().toString(), "rb", rolledBack ? 1 : 0));
        }
        return data.batch("UPDATE rock_world_log SET rolled_back = :rb WHERE id = :id", batch)
                .thenApply(counts -> null);
    }
}
