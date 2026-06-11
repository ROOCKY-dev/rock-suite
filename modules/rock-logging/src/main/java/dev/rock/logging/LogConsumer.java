package dev.rock.logging;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.domain.RockWorldLogEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CoreProtect-style async consumer: world changes are enqueued from the event
 * listener (tick thread, O(1)) and drained to the database in batches by a
 * virtual-thread worker. Survives write bursts without touching tick time.
 */
@RockInternal
public final class LogConsumer implements AutoCloseable {

    private static final int MAX_BATCH = 200;
    private static final long DRAIN_INTERVAL_MS = 250;

    private static final Logger log = LoggerFactory.getLogger(LogConsumer.class);

    private final WorldLogRepository repository;
    private final ConcurrentLinkedQueue<RockWorldLogEntry> queue = new ConcurrentLinkedQueue<>();
    private final Thread worker;
    private volatile boolean running = true;

    public LogConsumer(WorldLogRepository repository) {
        this.repository = repository;
        this.worker = Thread.ofVirtual().name("rock-log-consumer").start(this::drainLoop);
    }

    /** O(1), allocation-only; safe on the tick thread. */
    public void enqueue(RockWorldLogEntry entry) {
        queue.add(entry);
    }

    public int depth() {
        return queue.size();
    }

    /** Writes everything currently queued; used by shutdown and tests. */
    public CompletableFuture<Void> flush() {
        List<RockWorldLogEntry> batch = drain(Integer.MAX_VALUE);
        if (batch.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return repository.insertBatch(batch);
    }

    private List<RockWorldLogEntry> drain(int max) {
        List<RockWorldLogEntry> batch = new ArrayList<>();
        RockWorldLogEntry entry;
        while (batch.size() < max && (entry = queue.poll()) != null) {
            batch.add(entry);
        }
        return batch;
    }

    private void drainLoop() {
        while (running) {
            try {
                List<RockWorldLogEntry> batch = drain(MAX_BATCH);
                if (!batch.isEmpty()) {
                    repository.insertBatch(batch).join();
                }
                Thread.sleep(DRAIN_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("World-log batch write failed; entries are dropped from this batch", e);
            }
        }
    }

    @Override
    public void close() {
        running = false;
        worker.interrupt();
        try {
            flush().join();
        } catch (Exception e) {
            log.error("Final world-log flush failed", e);
        }
    }
}
