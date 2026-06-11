package dev.rock.api.scheduler;

import dev.rock.api.service.RockService;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Platform task scheduling (RPS §7). Database, HTTP, and Discord work MUST run
 * via the async methods — the Minecraft tick thread never blocks (TRS §3).
 */
public interface Scheduler extends RockService {

    /** Runs on the main server (tick) thread. */
    TaskHandle runSync(Runnable task);

    /** Runs on a platform worker thread (virtual thread). */
    TaskHandle runAsync(Runnable task);

    /** Runs async and exposes the result. */
    <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier);

    TaskHandle runLater(Runnable task, Duration delay);

    TaskHandle runRepeating(Runnable task, Duration initialDelay, Duration period);
}
