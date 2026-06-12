package dev.rock.permissions;

import dev.rock.api.domain.ContextSet;
import dev.rock.api.domain.PermissionState;
import dev.rock.api.domain.RockGroup;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Storage abstraction for permission data; backed by DataService in production. */
public interface PermissionRepository {

    /**
     * One stored permission node.
     *
     * @param expires null = permanent
     */
    record PermNode(String node, ContextSet context, PermissionState state, Instant expires) {

        public boolean expired(Instant now) {
            return expires != null && !expires.isAfter(now);
        }
    }

    record Snapshot(
            Map<UUID, RockGroup> groups,
            Map<UUID, List<PermNode>> groupPermissions,
            Map<UUID, List<PermNode>> playerPermissions,
            Map<UUID, Set<UUID>> playerGroups,
            Map<UUID, Map<String, String>> playerOptions,
            Map<UUID, Map<String, String>> groupOptions) {

        public static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    /** Full state load used to (re)build the evaluation cache. */
    CompletableFuture<Snapshot> snapshot();

    CompletableFuture<Void> savePlayerPermission(
            UUID playerId, String node, ContextSet context, PermissionState state, Instant expires);

    CompletableFuture<Void> deletePlayerPermission(UUID playerId, String node, ContextSet context);

    CompletableFuture<Void> saveGroup(RockGroup group);

    CompletableFuture<Void> saveGroupPermission(
            UUID groupId, String node, ContextSet context, PermissionState state, Instant expires);

    CompletableFuture<Void> assignGroup(UUID playerId, UUID groupId);

    CompletableFuture<List<RockGroup>> groupsOf(UUID playerId);

    CompletableFuture<Void> savePlayerOption(UUID playerId, String key, String value);

    CompletableFuture<Void> saveGroupOption(UUID groupId, String key, String value);

    /** Deletes expired temporary permissions; returns how many were removed. */
    CompletableFuture<Integer> purgeExpired(Instant now);
}
