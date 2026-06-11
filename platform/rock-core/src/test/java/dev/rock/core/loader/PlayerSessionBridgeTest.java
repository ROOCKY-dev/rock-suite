package dev.rock.core.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rock.api.domain.PlayerStatus;
import dev.rock.api.domain.RockPlayer;
import dev.rock.api.event.EventBus;
import dev.rock.api.events.player.PlayerJoinEvent;
import dev.rock.api.events.player.PlayerLeaveEvent;
import dev.rock.api.services.PlayerService;
import dev.rock.core.event.DefaultEventBus;
import dev.rock.core.service.DefaultServiceRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PlayerSessionBridgeTest {

    private final DefaultServiceRegistry registry = new DefaultServiceRegistry();
    private final EventBus bus = new DefaultEventBus(Executors.newVirtualThreadPerTaskExecutor());
    private final PlayerSessionBridge bridge = new PlayerSessionBridge(registry, bus);

    private static RockPlayer player(UUID id, String name, Instant first, Instant last) {
        return new RockPlayer(id, name, Locale.ROOT, first, last, PlayerStatus.ACTIVE, null);
    }

    /** Minimal PlayerService double: recordJoin returns a canned player. */
    private static PlayerService fakeService(RockPlayer canned) {
        return new PlayerService() {
            @Override
            public CompletableFuture<Optional<RockPlayer>> findById(UUID id) {
                return CompletableFuture.completedFuture(Optional.of(canned));
            }

            @Override
            public CompletableFuture<Optional<RockPlayer>> findByUsername(String username) {
                return CompletableFuture.completedFuture(Optional.of(canned));
            }

            @Override
            public CompletableFuture<RockPlayer> recordJoin(UUID id, String username) {
                return CompletableFuture.completedFuture(canned);
            }

            @Override
            public CompletableFuture<Void> recordLeave(UUID id) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<List<RockPlayer>> online() {
                return CompletableFuture.completedFuture(List.of(canned));
            }

            @Override
            public CompletableFuture<RockPlayer> erase(UUID id) {
                return CompletableFuture.completedFuture(canned);
            }
        };
    }

    @Test
    void joinWithPlayerServicePublishesPersistedPlayerWithFirstJoinFlag() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        registry.register(PlayerService.class, fakeService(player(id, "Ahmed", now, now)));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PlayerJoinEvent> received = new AtomicReference<>();
        bus.subscribe(PlayerJoinEvent.class, e -> {
            received.set(e);
            latch.countDown();
        });

        bridge.playerJoined(id, "Ahmed");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(id, received.get().player().id());
        assertTrue(received.get().firstJoin(), "firstJoin == lastSeen must flag a first join");
    }

    @Test
    void returningPlayerIsNotFlaggedAsFirstJoin() throws Exception {
        UUID id = UUID.randomUUID();
        Instant first = Instant.now().minusSeconds(86_400);
        registry.register(PlayerService.class, fakeService(player(id, "Ahmed", first, Instant.now())));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PlayerJoinEvent> received = new AtomicReference<>();
        bus.subscribe(PlayerJoinEvent.class, e -> {
            received.set(e);
            latch.countDown();
        });

        bridge.playerJoined(id, "Ahmed");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertFalse(received.get().firstJoin());
    }

    @Test
    void joinWithoutPlayerServiceStillPublishesTransientPlayer() throws Exception {
        UUID id = UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PlayerJoinEvent> received = new AtomicReference<>();
        bus.subscribe(PlayerJoinEvent.class, e -> {
            received.set(e);
            latch.countDown();
        });

        bridge.playerJoined(id, "NoDataLayer");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals("NoDataLayer", received.get().player().username());
    }

    @Test
    void leavePublishesPlayerLeaveEvent() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        registry.register(PlayerService.class, fakeService(player(id, "Ahmed", now, now)));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PlayerLeaveEvent> received = new AtomicReference<>();
        bus.subscribe(PlayerLeaveEvent.class, e -> {
            received.set(e);
            latch.countDown();
        });

        bridge.playerLeft(id, "Ahmed");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(received.get());
        assertEquals(id, received.get().player().id());
    }
}
