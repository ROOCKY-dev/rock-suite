package dev.rock.api.metrics;

import dev.rock.api.service.RockService;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * Platform-wide observability registry (RPS §13). Implemented by rock-core so
 * core systems (EventBus, DataService) can record without module dependencies;
 * the rock-metrics module adds JVM gauges and the command/report surface.
 *
 * <p>Counter mutations are allocation-free and tick-thread safe.
 */
public interface MetricsRegistry extends RockService {

    void increment(String counter);

    void add(String counter, long delta);

    /** Registers a live gauge; re-registering a name replaces it. */
    void registerGauge(String name, DoubleSupplier supplier);

    /** Snapshot of all counters (name → count). */
    Map<String, Long> counters();

    /** Snapshot of all gauges, evaluated now (name → value). */
    Map<String, Double> gauges();
}
