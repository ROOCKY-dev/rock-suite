package dev.rock.discord;

import dev.rock.api.annotations.RockInternal;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rate-limited delivery queue (TRS §13): messages are never sent from the
 * game thread; failures retry with exponential backoff capped at 60 seconds.
 */
@RockInternal
public final class DiscordMessageQueue implements AutoCloseable {

    static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

    private static final Logger log = LoggerFactory.getLogger(DiscordMessageQueue.class);

    private record Pending(String channelId, String content, CompletableFuture<Void> done) {
    }

    private final DiscordGateway gateway;
    private final Duration sendInterval;
    private final LinkedBlockingQueue<Pending> queue = new LinkedBlockingQueue<>();
    private final Thread worker;
    private volatile boolean running = true;

    public DiscordMessageQueue(DiscordGateway gateway, Duration sendInterval) {
        this.gateway = Objects.requireNonNull(gateway);
        this.sendInterval = sendInterval;
        this.worker = Thread.ofVirtual().name("rock-discord-queue").start(this::drain);
    }

    /** Enqueues a message; the returned future completes after actual delivery. */
    public CompletableFuture<Void> enqueue(String channelId, String content) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        if (!running) {
            done.completeExceptionally(new IllegalStateException("Discord queue is shut down"));
            return done;
        }
        queue.add(new Pending(channelId, content, done));
        return done;
    }

    public int depth() {
        return queue.size();
    }

    private void drain() {
        Duration backoff = sendInterval;
        while (running || !queue.isEmpty()) {
            Pending pending;
            try {
                pending = queue.poll(250, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (pending == null) {
                continue;
            }
            boolean delivered = false;
            while (!delivered && running) {
                try {
                    gateway.send(pending.channelId(), pending.content()).join();
                    pending.done().complete(null);
                    delivered = true;
                    backoff = sendInterval;
                    sleep(sendInterval);
                } catch (Exception e) {
                    // Exponential backoff, capped at 60s (TRS §13).
                    backoff = backoff.multipliedBy(2);
                    if (backoff.compareTo(MAX_BACKOFF) > 0) {
                        backoff = MAX_BACKOFF;
                    }
                    log.warn("Discord delivery failed; retrying in {}s", backoff.toSeconds(), e);
                    sleep(backoff);
                }
            }
            if (!delivered) {
                pending.done().completeExceptionally(new IllegalStateException("Queue shut down before delivery"));
            }
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            worker.join(Duration.ofSeconds(5).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
