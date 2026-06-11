package dev.rock.permissions;

import dev.rock.api.domain.PermissionState;
import dev.rock.api.domain.RockGroup;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Storage abstraction for permission data; backed by DataService in production. */
public interface PermissionRepository {

    record Snapshot(
            Map<UUID, RockGroup> groups,
            Map<UUID, Map<String, PermissionState>> groupPermissions,
            Map<UUID, Map<String, PermissionState>> playerPermissions,
            Map<UUID, Set<UUID>> playerGroups) {
    }

    /** Full state load used to (re)build the evaluation cache. */
    CompletableFuture<Snapshot> snapshot();

    CompletableFuture<Void> savePlayerPermission(UUID playerId, String node, PermissionState state);

    CompletableFuture<Void> deletePlayerPermission(UUID playerId, String node);

    CompletableFuture<Void> saveGroup(RockGroup group);

    CompletableFuture<Void> saveGroupPermission(UUID groupId, String node, PermissionState state);

    CompletableFuture<Void> assignGroup(UUID playerId, UUID groupId);

    CompletableFuture<List<RockGroup>> groupsOf(UUID playerId);
}
