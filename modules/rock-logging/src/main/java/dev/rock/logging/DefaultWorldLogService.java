package dev.rock.logging;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.domain.RockItemLogEntry;
import dev.rock.api.domain.RockWorldLogEntry;
import dev.rock.api.event.EventBus;
import dev.rock.api.event.EventPriority;
import dev.rock.api.event.Subscription;
import dev.rock.api.events.world.BlockChangeEvent;
import dev.rock.api.events.world.BlockChangeType;
import dev.rock.api.events.world.ItemFlowEvent;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.api.services.LogQuery;
import dev.rock.api.services.RollbackPreview;
import dev.rock.api.services.WorldLogService;
import dev.rock.api.world.WorldMutator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
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

    private static final int ITEM_BATCH_TRIGGER = 200;

    private final WorldLogRepository repository;
    private final LogConsumer consumer;
    private final EventBus eventBus;
    private final ServiceRegistry services;
    private final ConcurrentLinkedQueue<RockItemLogEntry> itemQueue = new ConcurrentLinkedQueue<>();
    private final List<Subscription> subscriptions = new ArrayList<>();

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
        subscriptions.add(eventBus.subscribe(BlockChangeEvent.class, EventPriority.LAST, false, event ->
                consumer.enqueue(new RockWorldLogEntry(
                        UUID.randomUUID(), event.actor(), event.fakePlayer(), event.worldId(),
                        event.x(), event.y(), event.z(), event.type(),
                        event.blockBefore(), event.blockAfter(), Instant.now(), false))));
        subscriptions.add(eventBus.subscribe(ItemFlowEvent.class, EventPriority.LAST, false, event -> {
            itemQueue.add(new RockItemLogEntry(
                    UUID.randomUUID(), event.actor(), event.fakePlayer(), event.worldId(),
                    event.x(), event.y(), event.z(), event.direction(), event.itemId(),
                    event.count(), Instant.now()));
            if (itemQueue.size() >= ITEM_BATCH_TRIGGER) {
                flushItems();
            }
        }));
        log.info("World logging active (async batched consumer; block + container tracking)");
    }

    @Override
    public void onDisable() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
        flushItems().join();
        consumer.close();
    }

    private CompletableFuture<Void> flushItems() {
        List<RockItemLogEntry> batch = new ArrayList<>();
        RockItemLogEntry entry;
        while ((entry = itemQueue.poll()) != null) {
            batch.add(entry);
        }
        return batch.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : repository.insertItemBatch(batch);
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
        return CompletableFuture.allOf(consumer.flush(), flushItems());
    }

    @Override
    public CompletableFuture<List<RockItemLogEntry>> queryItems(LogQuery query) {
        return flushItems().thenCompose(v -> repository.findItems(query));
    }

    @Override
    public CompletableFuture<RollbackPreview> previewRollback(LogQuery query) {
        return consumer.flush()
                .thenCompose(v -> repository.find(query, false, false))
                .thenApply(entries -> {
                    Map<BlockChangeType, Integer> byAction = new HashMap<>();
                    Map<String, Integer> byBlock = new HashMap<>();
                    for (RockWorldLogEntry entry : entries) {
                        byAction.merge(entry.action(), 1, Integer::sum);
                        byBlock.merge(entry.blockBefore(), 1, Integer::sum);
                    }
                    return new RollbackPreview(entries.size(), byAction, byBlock);
                });
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
