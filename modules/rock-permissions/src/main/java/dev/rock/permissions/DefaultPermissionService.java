package dev.rock.permissions;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.domain.PermissionState;
import dev.rock.api.domain.RockGroup;
import dev.rock.api.domain.owner.PlayerOwner;
import dev.rock.api.event.EventBus;
import dev.rock.api.events.permission.PermissionGrantedEvent;
import dev.rock.api.events.permission.PermissionRevokedEvent;
import dev.rock.api.events.permission.RankAssignedEvent;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.services.PermissionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Permission evaluation engine (RPS §12). Reads are served from an immutable
 * in-memory snapshot — {@link #has} is tick-thread safe and allocation-light
 * (TRS §3 event-processing budget). Mutations persist asynchronously, then
 * rebuild the snapshot.
 *
 * <p>Evaluation order: player-specific node, then groups by DMS resolution
 * order (priority ascending, name ascending on ties — the alphabetically
 * earlier group wins a same-priority conflict). Wildcard nodes
 * ({@code rock.claims.*}) match progressively shorter prefixes.
 */
@RockInternal
@Singleton
public final class DefaultPermissionService implements PermissionService, LifecycleAware {

    private static final Logger log = LoggerFactory.getLogger(DefaultPermissionService.class);

    private final PermissionRepository repository;
    private final EventBus eventBus;
    private final AtomicReference<PermissionRepository.Snapshot> cache =
            new AtomicReference<>(emptySnapshot());

    @Inject
    public DefaultPermissionService(PermissionRepository repository, EventBus eventBus) {
        this.repository = repository;
        this.eventBus = eventBus;
    }

    private static PermissionRepository.Snapshot emptySnapshot() {
        return new PermissionRepository.Snapshot(Map.of(), Map.of(), Map.of(), Map.of());
    }

    @Override
    public boolean has(UUID playerId, String node) {
        return check(playerId, node) == PermissionState.ALLOW;
    }

    @Override
    public PermissionState check(UUID playerId, String node) {
        PermissionRepository.Snapshot snapshot = cache.get();

        PermissionState direct = lookup(snapshot.playerPermissions().get(playerId), node);
        if (direct != PermissionState.UNSET) {
            return direct;
        }

        List<RockGroup> ordered = new ArrayList<>();
        for (UUID groupId : snapshot.playerGroups().getOrDefault(playerId, Set.of())) {
            RockGroup group = snapshot.groups().get(groupId);
            if (group != null && group.active()) {
                ordered.add(group);
            }
        }
        ordered.sort(RockGroup.RESOLUTION_ORDER);

        for (RockGroup group : ordered) {
            PermissionState state = lookup(snapshot.groupPermissions().get(group.id()), node);
            if (state != PermissionState.UNSET) {
                return state;
            }
        }
        return PermissionState.UNSET;
    }

    /** Exact node first, then wildcard fallbacks: a.b.c → a.b.* → a.*. */
    private static PermissionState lookup(Map<String, PermissionState> nodes, String node) {
        if (nodes == null || nodes.isEmpty()) {
            return PermissionState.UNSET;
        }
        PermissionState exact = nodes.get(node);
        if (exact != null) {
            return exact;
        }
        String prefix = node;
        int dot;
        while ((dot = prefix.lastIndexOf('.')) > 0) {
            prefix = prefix.substring(0, dot);
            PermissionState wildcard = nodes.get(prefix + ".*");
            if (wildcard != null) {
                return wildcard;
            }
        }
        return PermissionState.UNSET;
    }

    @Override
    public CompletableFuture<Void> grant(UUID playerId, String node) {
        return repository.savePlayerPermission(playerId, node, PermissionState.ALLOW)
                .thenCompose(v -> reload())
                .thenRun(() -> eventBus.publish(new PermissionGrantedEvent(new PlayerOwner(playerId), node)));
    }

    @Override
    public CompletableFuture<Void> deny(UUID playerId, String node) {
        return repository.savePlayerPermission(playerId, node, PermissionState.DENY)
                .thenCompose(v -> reload());
    }

    @Override
    public CompletableFuture<Void> unset(UUID playerId, String node) {
        return repository.deletePlayerPermission(playerId, node)
                .thenCompose(v -> reload())
                .thenRun(() -> eventBus.publish(new PermissionRevokedEvent(new PlayerOwner(playerId), node)));
    }

    @Override
    public CompletableFuture<RockGroup> createGroup(String name, int priority) {
        PermissionRepository.Snapshot snapshot = cache.get();
        boolean duplicatePriority = snapshot.groups().values().stream()
                .anyMatch(g -> g.priority() == priority && g.active());
        if (duplicatePriority) {
            // DMS tie-breaking rule: same priority is legal but warned about.
            log.warn("Group '{}' created with duplicate priority {}; ties resolve alphabetically", name, priority);
        }
        RockGroup group = new RockGroup(UUID.randomUUID(), name, priority, null);
        return repository.saveGroup(group).thenCompose(v -> reload()).thenApply(v -> group);
    }

    @Override
    public CompletableFuture<Void> grantGroup(UUID groupId, String node) {
        return repository.saveGroupPermission(groupId, node, PermissionState.ALLOW)
                .thenCompose(v -> reload());
    }

    @Override
    public CompletableFuture<Void> assignGroup(UUID playerId, UUID groupId) {
        return repository.assignGroup(playerId, groupId)
                .thenCompose(v -> reload())
                .thenRun(() -> {
                    RockGroup group = cache.get().groups().get(groupId);
                    if (group != null) {
                        eventBus.publish(new RankAssignedEvent(playerId, group));
                    }
                });
    }

    @Override
    public CompletableFuture<List<RockGroup>> groupsOf(UUID playerId) {
        return repository.groupsOf(playerId);
    }

    @Override
    public CompletableFuture<Void> reload() {
        return repository.snapshot().thenAccept(cache::set);
    }

    @Override
    public void onEnable() {
        reload().join();
        log.info("Permission cache loaded: {} group(s)", cache.get().groups().size());
    }

    @Override
    public void onDisable() {
        cache.set(emptySnapshot());
    }
}
