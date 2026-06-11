package dev.rock.api.services;

import dev.rock.api.domain.PermissionState;
import dev.rock.api.domain.RockGroup;
import dev.rock.api.service.RockService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Permission evaluation and storage contract (RPS §12). The synchronous
 * {@link #has} works against the in-memory evaluation cache and is safe on the
 * game thread; mutation methods are asynchronous.
 */
public interface PermissionService extends RockService {

    /** Cache-backed check, safe for the tick thread. */
    boolean has(UUID playerId, String node);

    PermissionState check(UUID playerId, String node);

    CompletableFuture<Void> grant(UUID playerId, String node);

    CompletableFuture<Void> deny(UUID playerId, String node);

    CompletableFuture<Void> unset(UUID playerId, String node);

    CompletableFuture<RockGroup> createGroup(String name, int priority);

    CompletableFuture<Void> grantGroup(UUID groupId, String node);

    CompletableFuture<Void> assignGroup(UUID playerId, UUID groupId);

    CompletableFuture<List<RockGroup>> groupsOf(UUID playerId);

    /** Live reload support (TRS §11): rebuilds the evaluation cache from storage. */
    CompletableFuture<Void> reload();
}
