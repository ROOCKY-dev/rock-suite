package dev.rock.core.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rock.api.event.Event;
import dev.rock.core.event.DefaultEventBus;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class DefaultMetricsRegistryTest {

    private final DefaultMetricsRegistry metrics = new DefaultMetricsRegistry();

    record PingEvent() implements Event {
    }

    @Test
    void countersAccumulate() {
        metrics.increment("a");
        metrics.increment("a");
        metrics.add("b", 40);

        assertEquals(2, metrics.counters().get("a"));
        assertEquals(40, metrics.counters().get("b"));
    }

    @Test
    void gaugesEvaluateLiveAndSurviveThrowingSuppliers() {
        int[] value = {1};
        metrics.registerGauge("x", () -> value[0]);
        metrics.registerGauge("broken", () -> {
            throw new IllegalStateException();
        });

        assertEquals(1.0, metrics.gauges().get("x"));
        value[0] = 7;
        assertEquals(7.0, metrics.gauges().get("x"));
        assertTrue(metrics.gauges().get("broken").isNaN(), "broken gauge reports NaN, not an exception");
    }

    @Test
    void eventBusCountsPublishesPerType() {
        DefaultEventBus bus = new DefaultEventBus(Executors.newVirtualThreadPerTaskExecutor(), metrics);

        bus.publish(new PingEvent());
        bus.publish(new PingEvent());

        assertEquals(2, metrics.counters().get("events.PingEvent"));
    }
}
