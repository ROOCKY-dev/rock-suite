package dev.rock.permissions;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.data.DataService;
import dev.rock.api.data.RowMapper;
import dev.rock.api.domain.ContextSet;
import dev.rock.api.domain.PermissionState;
import dev.rock.api.domain.RockGroup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Permission storage on the platform DataService — zero JDBC/JDBI imports (TRS §5). */
@RockInternal
@Singleton
public final class DataServicePermissionRepository implements PermissionRepository {

    private static final RowMapper<RockGroup> GROUP_MAPPER = row -> new RockGroup(
            row.getUuid("id"), row.getString("name"), row.getInt("priority"), row.getInstant("deleted_at"));

    private final DataService data;

    @Inject
    public DataServicePermissionRepository(DataService data) {
        this.data = data;
    }

    @Override
    public CompletableFuture<Snapshot> snapshot() {
        return data.inTransaction(tx -> {
            Map<UUID, RockGroup> groups = new HashMap<>();
            for (RockGroup group : tx.query(
                    "SELECT * FROM rock_groups WHERE deleted_at IS NULL", Map.of(), GROUP_MAPPER)) {
                groups.put(group.id(), group);
            }

            Map<UUID, List<PermNode>> groupPerms = new HashMap<>();
            tx.query("SELECT group_id, node, context, state, expires FROM rock_group_permissions", Map.of(), row -> {
                groupPerms.computeIfAbsent(row.getUuid("group_id"), k -> new ArrayList<>())
                        .add(new PermNode(row.getString("node"), ContextSet.deserialize(row.getString("context")),
                                PermissionState.valueOf(row.getString("state")), row.getInstant("expires")));
                return null;
            });

            Map<UUID, List<PermNode>> playerPerms = new HashMap<>();
            tx.query("SELECT player_id, node, context, state, expires FROM rock_player_permissions", Map.of(), row -> {
                playerPerms.computeIfAbsent(row.getUuid("player_id"), k -> new ArrayList<>())
                        .add(new PermNode(row.getString("node"), ContextSet.deserialize(row.getString("context")),
                                PermissionState.valueOf(row.getString("state")), row.getInstant("expires")));
                return null;
            });

            Map<UUID, Set<UUID>> playerGroups = new HashMap<>();
            tx.query("SELECT player_id, group_id FROM rock_player_groups", Map.of(), row -> {
                playerGroups.computeIfAbsent(row.getUuid("player_id"), k -> new HashSet<>())
                        .add(row.getUuid("group_id"));
                return null;
            });

            Map<UUID, Map<String, String>> playerOptions = new HashMap<>();
            tx.query("SELECT player_id, opt_key, opt_value FROM rock_player_options", Map.of(), row -> {
                playerOptions.computeIfAbsent(row.getUuid("player_id"), k -> new HashMap<>())
                        .put(row.getString("opt_key"), row.getString("opt_value"));
                return null;
            });

            Map<UUID, Map<String, String>> groupOptions = new HashMap<>();
            tx.query("SELECT group_id, opt_key, opt_value FROM rock_group_options", Map.of(), row -> {
                groupOptions.computeIfAbsent(row.getUuid("group_id"), k -> new HashMap<>())
                        .put(row.getString("opt_key"), row.getString("opt_value"));
                return null;
            });

            return new Snapshot(groups, groupPerms, playerPerms, playerGroups, playerOptions, groupOptions);
        });
    }

    private CompletableFuture<Void> savePermission(String table, String idColumn, UUID subjectId,
            String node, ContextSet context, PermissionState state, Instant expires) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", subjectId.toString());
        params.put("node", node);
        params.put("ctx", context.serialize());
        params.put("state", state.name());
        params.put("expires", expires == null ? null : expires.toEpochMilli());
        return data.inTransaction(tx -> {
            tx.update("DELETE FROM " + table + " WHERE " + idColumn + " = :id AND node = :node AND context = :ctx",
                    Map.of("id", subjectId.toString(), "node", node, "ctx", context.serialize()));
            tx.update("INSERT INTO " + table + " (" + idColumn + ", node, context, state, expires)"
                    + " VALUES (:id, :node, :ctx, :state, :expires)", params);
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> savePlayerPermission(
            UUID playerId, String node, ContextSet context, PermissionState state, Instant expires) {
        return savePermission("rock_player_permissions", "player_id", playerId, node, context, state, expires);
    }

    @Override
    public CompletableFuture<Void> deletePlayerPermission(UUID playerId, String node, ContextSet context) {
        return data.update(
                "DELETE FROM rock_player_permissions WHERE player_id = :p AND node = :n AND context = :ctx",
                Map.of("p", playerId.toString(), "n", node, "ctx", context.serialize()))
                .thenApply(rows -> null);
    }

    @Override
    public CompletableFuture<Void> saveGroup(RockGroup group) {
        return data.inTransaction(tx -> {
            tx.update("DELETE FROM rock_groups WHERE id = :id", Map.of("id", group.id().toString()));
            tx.update("INSERT INTO rock_groups (id, name, priority, deleted_at) VALUES (:id, :name, :priority, NULL)",
                    Map.of("id", group.id().toString(), "name", group.name(), "priority", group.priority()));
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> saveGroupPermission(
            UUID groupId, String node, ContextSet context, PermissionState state, Instant expires) {
        return savePermission("rock_group_permissions", "group_id", groupId, node, context, state, expires);
    }

    @Override
    public CompletableFuture<Void> assignGroup(UUID playerId, UUID groupId) {
        return data.inTransaction(tx -> {
            tx.update("DELETE FROM rock_player_groups WHERE player_id = :p AND group_id = :g",
                    Map.of("p", playerId.toString(), "g", groupId.toString()));
            tx.update("INSERT INTO rock_player_groups (player_id, group_id) VALUES (:p, :g)",
                    Map.of("p", playerId.toString(), "g", groupId.toString()));
            return null;
        });
    }

    @Override
    public CompletableFuture<List<RockGroup>> groupsOf(UUID playerId) {
        return data.query("""
                SELECT g.* FROM rock_groups g
                JOIN rock_player_groups pg ON pg.group_id = g.id
                WHERE pg.player_id = :p AND g.deleted_at IS NULL
                ORDER BY g.priority, g.name
                """,
                Map.of("p", playerId.toString()), GROUP_MAPPER);
    }

    private CompletableFuture<Void> saveOption(String table, String idColumn, UUID subjectId,
            String key, String value) {
        return data.inTransaction(tx -> {
            tx.update("DELETE FROM " + table + " WHERE " + idColumn + " = :id AND opt_key = :k",
                    Map.of("id", subjectId.toString(), "k", key));
            tx.update("INSERT INTO " + table + " (" + idColumn + ", opt_key, opt_value) VALUES (:id, :k, :v)",
                    Map.of("id", subjectId.toString(), "k", key, "v", value));
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> savePlayerOption(UUID playerId, String key, String value) {
        return saveOption("rock_player_options", "player_id", playerId, key, value);
    }

    @Override
    public CompletableFuture<Void> saveGroupOption(UUID groupId, String key, String value) {
        return saveOption("rock_group_options", "group_id", groupId, key, value);
    }

    @Override
    public CompletableFuture<Integer> purgeExpired(Instant now) {
        return data.inTransaction(tx -> {
            int purged = tx.update("DELETE FROM rock_player_permissions WHERE expires IS NOT NULL AND expires <= :now",
                    Map.of("now", now.toEpochMilli()));
            purged += tx.update("DELETE FROM rock_group_permissions WHERE expires IS NOT NULL AND expires <= :now",
                    Map.of("now", now.toEpochMilli()));
            return purged;
        });
    }
}
