package dev.rock.logging;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.domain.RockWorldLogEntry;
import dev.rock.api.event.EventBus;
import dev.rock.api.event.EventPriority;
import dev.rock.api.event.Subscription;
import dev.rock.api.events.world.BlockChangeEvent;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.api.services.LogQuery;
import dev.rock.api.services.WorldLogService;
import dev.rock.api.world.WorldMutator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Block logging & rollback engine (the CoreProtect/Ledger answer). Records at
 * LAST priority — cancelled (protected) changes are never logged because they
 * never happen. Rollback applies inverse changes through the loader-provided
 * {@link WorldMutator} and keeps entries flagged for restore.
 */
@RockInternal
@Singleton
public final class DefaultWorldLogService implements WorldLogService, LifecycleAware {

    private static final Logger log = LoggerFactory.getLogger(DefaultWorldLogService.class);

    private final WorldLogRepository repository;
    private final LogConsumer consumer;
    private final EventBus eventBus;
    private final ServiceRegistry services;
    private Subscription subscription;

    @Inject
    public DefaultWorldLogService(
            WorldLogRepository repository, LogConsumer consumer, EventBus eventBus, ServiceRegistry services) {
        this.repository = repository;
        this.consumer = consumer;
        this.eventBus = eventBus;
        this.services = services;
    }

    @Override
    public void onEnable() {
        subscription = eventBus.subscribe(BlockChangeEvent.class, EventPriority.LAST, false, event ->
                consumer.enqueue(new RockWorldLogEntry(
                        UUID.randomUUID(), event.actor(), event.fakePlayer(), event.worldId(),
                        event.x(), event.y(), event.z(), event.type(),
                        event.blockBefore(), event.blockAfter(), Instant.now(), false)));
        log.info("World logging active (async batched consumer)");
    }

    @Override
    public void onDisable() {
        if (subscription != null) {
            subscription.close();
        }
        consumer.close();
    }

    @Override
    public CompletableFuture<List<RockWorldLogEntry>> query(LogQuery query) {
        return consumer.flush().thenCompose(v -> repository.find(query, null, false));
    }

    @Override
    public CompletableFuture<Integer> rollback(LogQuery query) {
        WorldMutator mutator = requireMutator();
        // Newest first: undoing in reverse order reconstructs the prior state.
        return consumer.flush()
                .thenCompose(v -> repository.find(query, false, false))
                .thenCompose(entries -> applyAll(entries, mutator, true));
    }

    @Override
    public CompletableFuture<Integer> restore(LogQuery query) {
        WorldMutator mutator = requireMutator();
        // Oldest first: replaying in original order reconstructs the later state.
        return consumer.flush()
                .thenCompose(v -> repository.find(query, true, true))
                .thenCompose(entries -> applyAll(entries, mutator, false));
    }

    @Override
    public CompletableFuture<Void> flush() {
        return consumer.flush();
    }

    private CompletableFuture<Integer> applyAll(
            List<RockWorldLogEntry> entries, WorldMutator mutator, boolean rollback) {
        if (entries.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (RockWorldLogEntry entry : entries) {
            String target = rollback ? entry.blockBefore() : entry.blockAfter();
            chain = chain.thenCompose(v ->
                    mutator.setBlock(entry.worldId(), entry.x(), entry.y(), entry.z(), target));
        }
        return chain
                .thenCompose(v -> repository.markRolledBack(entries, rollback))
                .thenApply(v -> {
                    log.info("{} {} world-log entr{}", rollback ? "Rolled back" : "Restored",
                            entries.size(), entries.size() == 1 ? "y" : "ies");
                    return entries.size();
                });
    }

    private WorldMutator requireMutator() {
        return services.find(WorldMutator.class).orElseThrow(() -> new IllegalStateException(
                "No WorldMutator registered — the loader adapter must provide world mutation "
                        + "support before rollback/restore can run"));
    }
}
