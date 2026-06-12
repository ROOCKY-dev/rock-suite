package dev.rock.core.metrics;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.metrics.MetricsRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.DoubleSupplier;

/** LongAdder-backed metrics registry — increments are contention-free (TRS §3 budget). */
@RockInternal
public final class DefaultMetricsRegistry implements MetricsRegistry {

    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final Map<String, DoubleSupplier> gauges = new ConcurrentHashMap<>();

    @Override
    public void increment(String counter) {
        counters.computeIfAbsent(counter, k -> new LongAdder()).increment();
    }

    @Override
    public void add(String counter, long delta) {
        counters.computeIfAbsent(counter, k -> new LongAdder()).add(delta);
    }

    @Override
    public void registerGauge(String name, DoubleSupplier supplier) {
        gauges.put(name, supplier);
    }

    @Override
    public Map<String, Long> counters() {
        Map<String, Long> snapshot = new TreeMap<>();
        counters.forEach((name, adder) -> snapshot.put(name, adder.sum()));
        return snapshot;
    }

    @Override
    public Map<String, Double> gauges() {
        Map<String, Double> snapshot = new TreeMap<>();
        gauges.forEach((name, supplier) -> {
            try {
                snapshot.put(name, supplier.getAsDouble());
            } catch (Exception e) {
                snapshot.put(name, Double.NaN);
            }
        });
        return new HashMap<>(snapshot);
    }
}
