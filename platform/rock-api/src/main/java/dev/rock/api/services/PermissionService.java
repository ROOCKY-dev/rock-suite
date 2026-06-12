package dev.rock.api.services;

import dev.rock.api.domain.ContextSet;
import dev.rock.api.domain.PermissionState;
import dev.rock.api.domain.RockGroup;
import dev.rock.api.service.RockService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Permission evaluation and storage contract (RPS §12).
 *
 * <p>Synchronous reads ({@link #has}, {@link #check}, {@link #option}) are
 * served from the in-memory evaluation cache and are safe on the game thread;
 * mutations are asynchronous.
 *
 * <p>Since 1.2: context scoping (world/dimension/claim/... — see
 * {@link ContextSet}), typed options/meta (prefix, suffix,
 * {@code rock.essentials.homes.max}), and temporary permissions.
 */
public interface PermissionService extends RockService {

    // --- Evaluation (cache-backed, tick-thread safe) -----------------------

    default boolean has(UUID playerId, String node) {
        return has(playerId, node, ContextSet.empty());
    }

    boolean has(UUID playerId, String node, ContextSet context);

    default PermissionState check(UUID playerId, String node) {
        return check(playerId, node, ContextSet.empty());
    }

    PermissionState check(UUID playerId, String node, ContextSet context);

    /**
     * Resolved option/meta value: the player's own option wins, then groups in
     * resolution order. Conventional keys: {@code prefix}, {@code suffix},
     * {@code weight}, and module limits like {@code rock.essentials.homes.max}.
     */
    Optional<String> option(UUID playerId, String key);

    OptionalInt intOption(UUID playerId, String key);

    // --- Player permission mutations ---------------------------------------

    default CompletableFuture<Void> grant(UUID playerId, String node) {
        return grant(playerId, node, ContextSet.empty());
    }

    CompletableFuture<Void> grant(UUID playerId, String node, ContextSet context);

    /** Grant that expires automatically (temporary ranks/permissions). */
    CompletableFuture<Void> grantTemporary(UUID playerId, String node, Duration duration);

    default CompletableFuture<Void> deny(UUID playerId, String node) {
        return deny(playerId, node, ContextSet.empty());
    }

    CompletableFuture<Void> deny(UUID playerId, String node, ContextSet context);

    default CompletableFuture<Void> unset(UUID playerId, String node) {
        return unset(playerId, node, ContextSet.empty());
    }

    CompletableFuture<Void> unset(UUID playerId, String node, ContextSet context);

    // --- Groups -------------------------------------------------------------

    CompletableFuture<RockGroup> createGroup(String name, int priority);

    default CompletableFuture<Void> grantGroup(UUID groupId, String node) {
        return grantGroup(groupId, node, ContextSet.empty());
    }

    CompletableFuture<Void> grantGroup(UUID groupId, String node, ContextSet context);

    CompletableFuture<Void> assignGroup(UUID playerId, UUID groupId);

    CompletableFuture<List<RockGroup>> groupsOf(UUID playerId);

    // --- Options / meta ------------------------------------------------------

    CompletableFuture<Void> setPlayerOption(UUID playerId, String key, String value);

    CompletableFuture<Void> setGroupOption(UUID groupId, String key, String value);

    // --- Maintenance ---------------------------------------------------------

    /** Live reload support (TRS §11): rebuilds the evaluation cache from storage. */
    CompletableFuture<Void> reload();
}
