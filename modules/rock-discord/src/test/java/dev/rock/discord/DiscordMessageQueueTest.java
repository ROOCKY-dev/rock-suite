package dev.rock.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class DiscordMessageQueueTest {

    @Test
    void deliversMessagesInOrder() throws Exception {
        List<String> delivered = new CopyOnWriteArrayList<>();
        DiscordGateway gateway = (channel, content) -> {
            delivered.add(content);
            return CompletableFuture.completedFuture(null);
        };

        try (DiscordMessageQueue queue = new DiscordMessageQueue(gateway, Duration.ofMillis(1))) {
            CompletableFuture.allOf(
                    queue.enqueue("c", "one"),
                    queue.enqueue("c", "two"),
                    queue.enqueue("c", "three")).get(10, TimeUnit.SECONDS);
        }

        assertEquals(List.of("one", "two", "three"), delivered);
    }

    @Test
    void retriesWithBackoffUntilDeliverySucceeds() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        DiscordGateway flaky = (channel, content) -> {
            if (attempts.incrementAndGet() < 3) {
                return CompletableFuture.failedFuture(new IllegalStateException("rate limited"));
            }
            return CompletableFuture.completedFuture(null);
        };

        try (DiscordMessageQueue queue = new DiscordMessageQueue(flaky, Duration.ofMillis(1))) {
            queue.enqueue("c", "persistent").get(20, TimeUnit.SECONDS);
        }

        assertEquals(3, attempts.get(), "message must be retried until delivered");
    }

    @Test
    void backoffIsCappedAtSixtySeconds() {
        assertEquals(60, DiscordMessageQueue.MAX_BACKOFF.toSeconds());
    }

    @Test
    void rejectsEnqueueAfterShutdown() {
        DiscordGateway gateway = (channel, content) -> CompletableFuture.completedFuture(null);
        DiscordMessageQueue queue = new DiscordMessageQueue(gateway, Duration.ofMillis(1));
        queue.close();

        CompletableFuture<Void> result = queue.enqueue("c", "late");

        assertTrue(result.isCompletedExceptionally());
    }
}
