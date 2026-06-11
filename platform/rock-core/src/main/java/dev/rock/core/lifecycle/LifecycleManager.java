package dev.rock.core.lifecycle;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.service.RockService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers {@link LifecycleAware} services (via the platform Guice type
 * listener) and drives their callbacks: enable in discovery order, disable in
 * reverse (DIS §11).
 */
@RockInternal
public final class LifecycleManager implements RockService {

    private static final Logger log = LoggerFactory.getLogger(LifecycleManager.class);

    private final Queue<LifecycleAware> pending = new ConcurrentLinkedQueue<>();
    private final Deque<LifecycleAware> enabled = new ArrayDeque<>();

    /** Called by the Guice injection listener for every LifecycleAware instance. */
    public void discovered(LifecycleAware instance) {
        pending.add(instance);
    }

    /**
     * Enables everything discovered since the last call. Returns the instances
     * enabled so callers (the module loader) can disable them on crash isolation.
     */
    public synchronized List<LifecycleAware> enablePending() {
        List<LifecycleAware> batch = new ArrayList<>();
        LifecycleAware next;
        while ((next = pending.poll()) != null) {
            next.onEnable();
            enabled.push(next);
            batch.add(next);
        }
        return batch;
    }

    /** Disables specific instances (module crash isolation / unload). */
    public synchronized void disable(List<LifecycleAware> instances) {
        for (int i = instances.size() - 1; i >= 0; i--) {
            LifecycleAware instance = instances.get(i);
            try {
                instance.onDisable();
            } catch (Exception e) {
                log.error("onDisable failed for {}", instance.getClass().getName(), e);
            }
            enabled.remove(instance);
        }
    }

    /** Platform shutdown: disables everything in reverse enable order. */
    public synchronized void disableAll() {
        while (!enabled.isEmpty()) {
            LifecycleAware instance = enabled.pop();
            try {
                instance.onDisable();
            } catch (Exception e) {
                log.error("onDisable failed for {}", instance.getClass().getName(), e);
            }
        }
    }
}
