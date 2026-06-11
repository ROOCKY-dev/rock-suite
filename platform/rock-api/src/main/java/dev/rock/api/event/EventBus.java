package dev.rock.api.event;

import dev.rock.api.service.RockService;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Decouples modules: producers publish, consumers subscribe; neither knows the
 * other exists (RPS §6). Synchronous publishing runs listeners on the calling
 * thread in priority order. Asynchronous publishing runs listeners on platform
 * worker threads and must never be used to mutate world state (TRS §8).
 */
public interface EventBus extends RockService {

    /** Subscribes at {@link EventPriority#NORMAL}, ignoring cancelled events. */
    <E extends Event> Subscription subscribe(Class<E> eventType, Consumer<E> listener);

    <E extends Event> Subscription subscribe(Class<E> eventType, EventPriority priority, Consumer<E> listener);

    /**
     * @param receiveCancelled when true the listener is invoked even after the
     *                         event has been cancelled by an earlier priority
     */
    <E extends Event> Subscription subscribe(
            Class<E> eventType, EventPriority priority, boolean receiveCancelled, Consumer<E> listener);

    /**
     * Publishes synchronously on the calling thread. Returns the same event
     * instance so callers can inspect mutations and cancellation state.
     */
    <E extends Event> E publish(E event);

    /** Publishes on a platform worker thread; safe for database / web / Discord work. */
    <E extends Event> CompletableFuture<E> publishAsync(E event);
}
