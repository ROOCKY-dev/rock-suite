package dev.rock.permissions;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.domain.ContextSet;
import dev.rock.api.domain.PermissionState;
import dev.rock.api.domain.RockGroup;
import dev.rock.api.domain.owner.PlayerOwner;
import dev.rock.api.event.EventBus;
import dev.rock.api.events.permission.PermissionGrantedEvent;
import dev.rock.api.events.permission.PermissionRevokedEvent;
import dev.rock.api.events.permission.RankAssignedEvent;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.scheduler.Scheduler;
import dev.rock.api.scheduler.TaskHandle;
import dev.rock.api.services.PermissionService;
import dev.rock.permissions.PermissionRepository.PermNode;
import dev.rock.permissions.PermissionRepository.Snapshot;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Permission evaluation engine (RPS §12). Reads are served from an immutable
 * in-memory snapshot — tick-thread safe (TRS §3).
 *
 * <p>Evaluation order: player-specific nodes, then groups by DMS resolution
 * order (priority asc, name asc on ties). Within one subject: exact node
 * before wildcard ({@code rock.claims.*}); among applicable context-scoped
 * entries the most specific context wins; expired temporary nodes never apply.
 * Expired rows are also purged by a periodic sweep.
 */
@RockInternal
@Singleton
public final class DefaultPermissionService implements PermissionService, LifecycleAware {

    private static final Duration SWEEP_INTERVAL = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(DefaultPermissionService.class);

    private final PermissionRepository repository;
    private final EventBus eventBus;
    private final Scheduler scheduler;
    private final AtomicReference<Snapshot> cache = new AtomicReference<>(Snapshot.empty());
    private TaskHandle sweepTask;

    @Inject
    public DefaultPermissionService(PermissionRepository repository, EventBus eventBus, Scheduler scheduler) {
        this.repository = repository;
        this.eventBus = eventBus;
        this.scheduler = scheduler;
    }

    /** Test constructor without the periodic sweep. */
    public DefaultPermissionService(PermissionRepository repository, EventBus eventBus) {
        this(repository, eventBus, null);
    }

    // --- Evaluation ---------------------------------------------------------

    @Override
    public boolean has(UUID playerId, String node, ContextSet context) {
        return check(playerId, node, context) == PermissionState.ALLOW;
    }

    @Override
    public PermissionState check(UUID playerId, String node, ContextSet context) {
        Snapshot snapshot = cache.get();
        Instant now = Instant.now();

        PermissionState direct = lookup(snapshot.playerPermissions().get(playerId), node, context, now);
        if (direct != PermissionState.UNSET) {
            return direct;
        }
        for (RockGroup group : orderedGroups(snapshot, playerId)) {
            PermissionState state = lookup(snapshot.groupPermissions().get(group.id()), node, context, now);
            if (state != PermissionState.UNSET) {
                return state;
            }
        }
        return PermissionState.UNSET;
    }

    private static List<RockGroup> orderedGroups(Snapshot snapshot, UUID playerId) {
        List<RockGroup> ordered = new ArrayList<>();
        for (UUID groupId : snapshot.playerGroups().getOrDefault(playerId, Set.of())) {
            RockGroup group = snapshot.groups().get(groupId);
            if (group != null && group.active()) {
                ordered.add(group);
            }
        }
        ordered.sort(RockGroup.RESOLUTION_ORDER);
        return ordered;
    }

    /** Exact node first, then wildcard fallbacks: a.b.c → a.b.* → a.*. */
    private static PermissionState lookup(List<PermNode> nodes, String node, ContextSet context, Instant now) {
        if (nodes == null || nodes.isEmpty()) {
            return PermissionState.UNSET;
        }
        PermissionState exact = lookupName(nodes, node, context, now);
        if (exact != PermissionState.UNSET) {
            return exact;
        }
        String prefix = node;
        int dot;
        while ((dot = prefix.lastIndexOf('.')) > 0) {
            prefix = prefix.substring(0, dot);
            PermissionState wildcard = lookupName(nodes, prefix + ".*", context, now);
            if (wildcard != PermissionState.UNSET) {
                return wildcard;
            }
        }
        return PermissionState.UNSET;
    }

    /** Most context-specific applicable entry for one node name wins. */
    private static PermissionState lookupName(List<PermNode> nodes, String name, ContextSet context, Instant now) {
        PermNode best = null;
        for (PermNode candidate : nodes) {
            if (!candidate.node().equals(name) || candidate.expired(now)
                    || !candidate.context().satisfiedBy(context)) {
                continue;
            }
            if (best == null || candidate.context().specificity() > best.context().specificity()) {
                best = candidate;
            }
        }
        return best == null ? PermissionState.UNSET : best.state();
    }

    @Override
    public Optional<String> option(UUID playerId, String key) {
        Snapshot snapshot = cache.get();
        Map<String, String> own = snapshot.playerOptions().get(playerId);
        if (own != null && own.containsKey(key)) {
            return Optional.of(own.get(key));
        }
        for (RockGroup group : orderedGroups(snapshot, playerId)) {
            Map<String, String> options = snapshot.groupOptions().get(group.id());
            if (options != null && options.containsKey(key)) {
                return Optional.of(options.get(key));
            }
        }
        return Optional.empty();
    }

    @Override
    public OptionalInt intOption(UUID playerId, String key) {
        return option(playerId, key).map(value -> {
            try {
                return OptionalInt.of(Integer.parseInt(value.trim()));
            } catch (NumberFormatException e) {
                log.warn("Option {} for {} is not numeric: '{}'", key, playerId, value);
                return OptionalInt.empty();
            }
        }).orElse(OptionalInt.empty());
    }

    // --- Mutations -----------------------------------------------------------

    @Override
    public CompletableFuture<Void> grant(UUID playerId, String node, ContextSet context) {
        return repository.savePlayerPermission(playerId, node, context, PermissionState.ALLOW, null)
                .thenCompose(v -> reload())
                .thenRun(() -> eventBus.publish(new PermissionGrantedEvent(new PlayerOwner(playerId), node)));
    }

    @Override
    public CompletableFuture<Void> grantTemporary(UUID playerId, String node, Duration duration) {
        Instant expires = Instant.now().plus(duration);
        return repository.savePlayerPermission(playerId, node, ContextSet.empty(), PermissionState.ALLOW, expires)
                .thenCompose(v -> reload())
                .thenRun(() -> eventBus.publish(new PermissionGrantedEvent(new PlayerOwner(playerId), node)));
    }

    @Override
    public CompletableFuture<Void> deny(UUID playerId, String node, ContextSet context) {
        return repository.savePlayerPermission(playerId, node, context, PermissionState.DENY, null)
                .thenCompose(v -> reload());
    }

    @Override
    public CompletableFuture<Void> unset(UUID playerId, String node, ContextSet context) {
        return repository.deletePlayerPermission(playerId, node, context)
                .thenCompose(v -> reload())
                .thenRun(() -> eventBus.publish(new PermissionRevokedEvent(new PlayerOwner(playerId), node)));
    }

    @Override
    public CompletableFuture<RockGroup> createGroup(String name, int priority) {
        boolean duplicatePriority = cache.get().groups().values().stream()
                .anyMatch(g -> g.priority() == priority && g.active());
        if (duplicatePriority) {
            // DMS tie-breaking rule: same priority is legal but warned about.
            log.warn("Group '{}' created with duplicate priority {}; ties resolve alphabetically", name, priority);
        }
        RockGroup group = new RockGroup(UUID.randomUUID(), name, priority, null);
        return repository.saveGroup(group).thenCompose(v -> reload()).thenApply(v -> group);
    }

    @Override
    public CompletableFuture<Void> grantGroup(UUID groupId, String node, ContextSet context) {
        return repository.saveGroupPermission(groupId, node, context, PermissionState.ALLOW, null)
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
    public CompletableFuture<Void> setPlayerOption(UUID playerId, String key, String value) {
        return repository.savePlayerOption(playerId, key, value).thenCompose(v -> reload());
    }

    @Override
    public CompletableFuture<Void> setGroupOption(UUID groupId, String key, String value) {
        return repository.saveGroupOption(groupId, key, value).thenCompose(v -> reload());
    }

    @Override
    public CompletableFuture<Void> reload() {
        return repository.snapshot().thenAccept(cache::set);
    }

    // --- Lifecycle ------------------------------------------------------------

    @Override
    public void onEnable() {
        reload().join();
        log.info("Permission cache loaded: {} group(s)", cache.get().groups().size());
        if (scheduler != null) {
            sweepTask = scheduler.runRepeating(this::sweepExpired, SWEEP_INTERVAL, SWEEP_INTERVAL);
        }
    }

    private void sweepExpired() {
        repository.purgeExpired(Instant.now()).thenCompose(purged -> {
            if (purged > 0) {
                log.info("Purged {} expired temporary permission(s)", purged);
                return reload();
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    @Override
    public void onDisable() {
        if (sweepTask != null) {
            sweepTask.cancel();
        }
        cache.set(Snapshot.empty());
    }
}
