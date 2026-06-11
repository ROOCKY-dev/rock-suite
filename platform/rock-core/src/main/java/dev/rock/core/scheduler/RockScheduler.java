package dev.rock.core.scheduler;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.scheduler.Scheduler;
import dev.rock.api.scheduler.TaskHandle;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Platform scheduler (RPS §7). Async work runs on virtual threads (Java 21);
 * sync work is handed to the loader-provided main-thread executor. The tick
 * thread is never used for I/O (TRS §3).
 */
@RockInternal
public final class RockScheduler implements Scheduler, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RockScheduler.class);

    private final Executor mainThread;
    private final ExecutorService asyncExecutor;
    private final ScheduledExecutorService timer;

    public RockScheduler(Executor mainThread, ExecutorService asyncExecutor, ScheduledExecutorService timer) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor");
        this.timer = Objects.requireNonNull(timer, "timer");
    }

    private static final class SimpleHandle implements TaskHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile ScheduledFuture<?> future;

        @Override
        public boolean cancelled() {
            return cancelled.get();
        }

        @Override
        public void cancel() {
            cancelled.set(true);
            ScheduledFuture<?> f = future;
            if (f != null) {
                f.cancel(false);
            }
        }
    }

    private Runnable guarded(Runnable task, SimpleHandle handle) {
        return () -> {
            if (handle.cancelled()) {
                return;
            }
            try {
                task.run();
            } catch (Exception e) {
                log.error("Scheduled task threw", e);
            }
        };
    }

    @Override
    public TaskHandle runSync(Runnable task) {
        SimpleHandle handle = new SimpleHandle();
        mainThread.execute(guarded(task, handle));
        return handle;
    }

    @Override
    public TaskHandle runAsync(Runnable task) {
        SimpleHandle handle = new SimpleHandle();
        asyncExecutor.execute(guarded(task, handle));
        return handle;
    }

    @Override
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, asyncExecutor);
    }

    @Override
    public TaskHandle runLater(Runnable task, Duration delay) {
        SimpleHandle handle = new SimpleHandle();
        handle.future = timer.schedule(
                () -> asyncExecutor.execute(guarded(task, handle)), delay.toMillis(), TimeUnit.MILLISECONDS);
        return handle;
    }

    @Override
    public TaskHandle runRepeating(Runnable task, Duration initialDelay, Duration period) {
        SimpleHandle handle = new SimpleHandle();
        handle.future = timer.scheduleAtFixedRate(
                () -> asyncExecutor.execute(guarded(task, handle)),
                initialDelay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
        return handle;
    }

    @Override
    public void close() {
        timer.shutdownNow();
        asyncExecutor.shutdown();
    }
}
