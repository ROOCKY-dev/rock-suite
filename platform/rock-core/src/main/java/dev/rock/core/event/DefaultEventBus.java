package dev.rock.core.event;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.event.Cancellable;
import dev.rock.api.event.Event;
import dev.rock.api.event.EventBus;
import dev.rock.api.event.EventPriority;
import dev.rock.api.event.Subscription;
import dev.rock.api.metrics.MetricsRegistry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Priority-ordered event bus (TRS §8). Listener exceptions are isolated: a
 * crashing listener is logged and the remaining listeners still run (TRS §17).
 * Delivery is by exact event class — listeners subscribe to concrete event types.
 */
@RockInternal
public final class DefaultEventBus implements EventBus {

    private static final Logger log = LoggerFactory.getLogger(DefaultEventBus.class);

    private final Map<Class<?>, List<Registered<?>>> listeners = new ConcurrentHashMap<>();
    private final Executor asyncExecutor;
    private final MetricsRegistry metrics;

    public DefaultEventBus(Executor asyncExecutor) {
        this(asyncExecutor, null);
    }

    public DefaultEventBus(Executor asyncExecutor, MetricsRegistry metrics) {
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor");
        this.metrics = metrics;
    }

    private record Registered<E>(
            EventPriority priority, boolean receiveCancelled, Consumer<E> consumer, AtomicBoolean active) {
    }

    @Override
    public <E extends Event> Subscription subscribe(Class<E> eventType, Consumer<E> listener) {
        return subscribe(eventType, EventPriority.NORMAL, false, listener);
    }

    @Override
    public <E extends Event> Subscription subscribe(Class<E> eventType, EventPriority priority, Consumer<E> listener) {
        return subscribe(eventType, priority, false, listener);
    }

    @Override
    public <E extends Event> Subscription subscribe(
            Class<E> eventType, EventPriority priority, boolean receiveCancelled, Consumer<E> listener) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(listener, "listener");

        Registered<E> registered = new Registered<>(priority, receiveCancelled, listener, new AtomicBoolean(true));
        List<Registered<?>> list = listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        list.add(registered);

        return new Subscription() {
            @Override
            public boolean active() {
                return registered.active().get();
            }

            @Override
            public void close() {
                if (registered.active().compareAndSet(true, false)) {
                    list.remove(registered);
                }
            }
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends Event> E publish(E event) {
        Objects.requireNonNull(event, "event");
        if (metrics != null) {
            metrics.increment("events." + event.getClass().getSimpleName());
        }
        List<Registered<?>> registered = listeners.get(event.getClass());
        if (registered == null || registered.isEmpty()) {
            return event;
        }
        for (EventPriority priority : EventPriority.values()) {
            for (Registered<?> r : registered) {
                if (r.priority() != priority || !r.active().get()) {
                    continue;
                }
                if (event instanceof Cancellable c && c.cancelled() && !r.receiveCancelled()) {
                    continue;
                }
                try {
                    ((Consumer<E>) r.consumer()).accept(event);
                } catch (Exception e) {
                    log.error("Event listener for {} threw; continuing with remaining listeners",
                            event.getClass().getSimpleName(), e);
                }
            }
        }
        return event;
    }

    @Override
    public <E extends Event> CompletableFuture<E> publishAsync(E event) {
        return CompletableFuture.supplyAsync(() -> publish(event), asyncExecutor);
    }
}
