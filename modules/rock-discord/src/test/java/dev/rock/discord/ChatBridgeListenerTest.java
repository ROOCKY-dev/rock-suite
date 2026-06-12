package dev.rock.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rock.api.event.EventBus;
import dev.rock.api.event.EventPriority;
import dev.rock.api.events.player.PlayerChatEvent;
import dev.rock.core.event.DefaultEventBus;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ChatBridgeListenerTest {

    private final EventBus eventBus = new DefaultEventBus(Executors.newVirtualThreadPerTaskExecutor());
    private final List<String> sent = new CopyOnWriteArrayList<>();
    private final DiscordMessageQueue queue = new DiscordMessageQueue(
            (channel, content) -> {
                sent.add(channel + "|" + content);
                return CompletableFuture.completedFuture(null);
            }, Duration.ofMillis(1));
    private ChatBridgeListener listener;

    @AfterEach
    void tearDown() {
        if (listener != null) {
            listener.onDisable();
        }
        queue.close();
    }

    private void await(int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (sent.size() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
    }

    @Test
    void bridgesChatToTheConfiguredChannel() throws Exception {
        listener = new ChatBridgeListener(eventBus, queue,
                new DiscordSettings("", 1, "chan-42"));
        listener.onEnable();

        eventBus.publish(new PlayerChatEvent(UUID.randomUUID(), "Alice", "hello world"));
        await(1);

        assertEquals(List.of("chan-42|**Alice**: hello world"), sent);
    }

    @Test
    void mutedChatIsNeverBridged() throws Exception {
        listener = new ChatBridgeListener(eventBus, queue,
                new DiscordSettings("", 1, "chan-42"));
        listener.onEnable();
        // A mute/filter listener cancels at EARLY priority.
        eventBus.subscribe(PlayerChatEvent.class, EventPriority.EARLY, PlayerChatEvent::cancel);

        eventBus.publish(new PlayerChatEvent(UUID.randomUUID(), "Spammer", "buy gold now"));
        eventBus.publish(new PlayerChatEvent(UUID.randomUUID(), "Spammer", "cheap diamonds"));
        Thread.sleep(150);

        assertTrue(sent.isEmpty(), "cancelled chat must not reach Discord");
    }

    @Test
    void disabledWithoutConfiguredChannel() throws Exception {
        listener = new ChatBridgeListener(eventBus, queue,
                new DiscordSettings("", 1, ""));
        listener.onEnable();

        eventBus.publish(new PlayerChatEvent(UUID.randomUUID(), "Alice", "hello"));
        Thread.sleep(100);

        assertTrue(sent.isEmpty());
    }
}
