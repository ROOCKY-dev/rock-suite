package dev.rock.permissions;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.data.DataService;
import dev.rock.api.data.RowMapper;
import dev.rock.api.domain.PermissionState;
import dev.rock.api.domain.RockGroup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
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

            Map<UUID, Map<String, PermissionState>> groupPerms = new HashMap<>();
            tx.query("SELECT group_id, node, state FROM rock_group_permissions", Map.of(), row -> {
                groupPerms.computeIfAbsent(row.getUuid("group_id"), k -> new HashMap<>())
                        .put(row.getString("node"), PermissionState.valueOf(row.getString("state")));
                return null;
            });

            Map<UUID, Map<String, PermissionState>> playerPerms = new HashMap<>();
            tx.query("SELECT player_id, node, state FROM rock_player_permissions", Map.of(), row -> {
                playerPerms.computeIfAbsent(row.getUuid("player_id"), k -> new HashMap<>())
                        .put(row.getString("node"), PermissionState.valueOf(row.getString("state")));
                return null;
            });

            Map<UUID, Set<UUID>> playerGroups = new HashMap<>();
            tx.query("SELECT player_id, group_id FROM rock_player_groups", Map.of(), row -> {
                playerGroups.computeIfAbsent(row.getUuid("player_id"), k -> new HashSet<>())
                        .add(row.getUuid("group_id"));
                return null;
            });

            return new Snapshot(groups, groupPerms, playerPerms, playerGroups);
        });
    }

    @Override
    public CompletableFuture<Void> savePlayerPermission(UUID playerId, String node, PermissionState state) {
        return data.inTransaction(tx -> {
            tx.update("DELETE FROM rock_player_permissions WHERE player_id = :p AND node = :n",
                    Map.of("p", playerId.toString(), "n", node));
            tx.update("INSERT INTO rock_player_permissions (player_id, node, state) VALUES (:p, :n, :s)",
                    Map.of("p", playerId.toString(), "n", node, "s", state.name()));
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> deletePlayerPermission(UUID playerId, String node) {
        return data.update("DELETE FROM rock_player_permissions WHERE player_id = :p AND node = :n",
                Map.of("p", playerId.toString(), "n", node)).thenApply(rows -> null);
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
    public CompletableFuture<Void> saveGroupPermission(UUID groupId, String node, PermissionState state) {
        return data.inTransaction(tx -> {
            tx.update("DELETE FROM rock_group_permissions WHERE group_id = :g AND node = :n",
                    Map.of("g", groupId.toString(), "n", node));
            tx.update("INSERT INTO rock_group_permissions (group_id, node, state) VALUES (:g, :n, :s)",
                    Map.of("g", groupId.toString(), "n", node, "s", state.name()));
            return null;
        });
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
}
