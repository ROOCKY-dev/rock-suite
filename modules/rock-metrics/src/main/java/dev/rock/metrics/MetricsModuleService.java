package dev.rock.metrics;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.command.CommandResult;
import dev.rock.api.command.CommandService;
import dev.rock.api.command.CommandSpec;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.metrics.MetricsRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;

/**
 * Observability surface (RPS §13): registers JVM gauges on the platform
 * MetricsRegistry and serves the /rock metrics report. Tick-duration gauges
 * arrive with the K3 packaging step (real tick hooks); spark remains the
 * deep-profiling tool — we integrate, we don't duplicate.
 */
@RockInternal
@Singleton
public final class MetricsModuleService implements LifecycleAware {

    private final MetricsRegistry metrics;
    private final CommandService commands;

    @Inject
    public MetricsModuleService(MetricsRegistry metrics, CommandService commands) {
        this.metrics = metrics;
        this.commands = commands;
    }

    @Override
    public void onEnable() {
        Runtime runtime = Runtime.getRuntime();
        metrics.registerGauge("jvm.heap_used_mb",
                () -> (runtime.totalMemory() - runtime.freeMemory()) / 1048576.0);
        metrics.registerGauge("jvm.heap_max_mb", () -> runtime.maxMemory() / 1048576.0);
        metrics.registerGauge("jvm.uptime_seconds",
                () -> ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0);
        metrics.registerGauge("jvm.threads",
                () -> (double) ManagementFactory.getThreadMXBean().getThreadCount());

        commands.register(new CommandSpec(List.of("metrics"), "Platform metrics report",
                "rock.admin.metrics", ctx -> {
            Map<String, Double> gauges = metrics.gauges();
            ctx.sender().sendMessage(String.format("Heap: %.1f / %.1f MB · Uptime: %.0fs · Threads: %.0f",
                    gauges.getOrDefault("jvm.heap_used_mb", 0.0),
                    gauges.getOrDefault("jvm.heap_max_mb", 0.0),
                    gauges.getOrDefault("jvm.uptime_seconds", 0.0),
                    gauges.getOrDefault("jvm.threads", 0.0)));

            Map<String, Long> counters = metrics.counters();
            long dataOps = counters.getOrDefault("data.operations", 0L);
            long dataMicros = counters.getOrDefault("data.operation_micros", 0L);
            ctx.sender().sendMessage(String.format("DataService: %d ops, avg %.2f ms",
                    dataOps, dataOps == 0 ? 0.0 : dataMicros / 1000.0 / dataOps));

            ctx.sender().sendMessage("Events:");
            counters.entrySet().stream()
                    .filter(e -> e.getKey().startsWith("events."))
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(8)
                    .forEach(e -> ctx.sender().sendMessage(
                            "  " + e.getKey().substring("events.".length()) + ": " + e.getValue()));
            return CommandResult.SUCCESS;
        }));
    }

    @Override
    public void onDisable() {
        commands.unregister(List.of("metrics"));
    }
}
