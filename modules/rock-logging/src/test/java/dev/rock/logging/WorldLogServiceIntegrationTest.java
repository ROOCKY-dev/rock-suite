package dev.rock.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.rock.api.domain.RockWorldLogEntry;
import dev.rock.api.event.EventBus;
import dev.rock.api.events.world.BlockChangeEvent;
import dev.rock.api.events.world.BlockChangeType;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.api.services.LogQuery;
import dev.rock.api.world.WorldMutator;
import dev.rock.core.event.DefaultEventBus;
import dev.rock.core.service.DefaultServiceRegistry;
import dev.rock.data.jdbi.JdbiDataService;
import dev.rock.data.migration.DataMigrator;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Full-stack logging tests: event layer → batched consumer → SQLite → rollback/restore. */
class WorldLogServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;
    private JdbiDataService data;
    private EventBus eventBus;
    private ServiceRegistry registry;
    private DefaultWorldLogService service;
    private final Map<String, String> fakeWorld = new ConcurrentHashMap<>();

    private final UUID world = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + tempDir.resolve("log.db"));
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        new DataMigrator(dataSource).migrate();
        data = new JdbiDataService(Jdbi.create(dataSource));
        eventBus = new DefaultEventBus(Executors.newVirtualThreadPerTaskExecutor());
        registry = new DefaultServiceRegistry();

        WorldLogRepository repository = new DataServiceWorldLogRepository(data);
        service = new DefaultWorldLogService(repository, new LogConsumer(repository), eventBus, registry);
        service.onEnable();
    }

    @AfterEach
    void tearDown() {
        service.onDisable();
        data.onDisable();
        dataSource.close();
    }

    private void registerMutator() {
        registry.register(WorldMutator.class, new WorldMutator() {
            @Override
            public CompletableFuture<Void> setBlock(UUID worldId, int x, int y, int z, String blockId) {
                fakeWorld.put(x + "," + y + "," + z, blockId);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<String> getBlock(UUID worldId, int x, int y, int z) {
                return CompletableFuture.completedFuture(fakeWorld.getOrDefault(x + "," + y + "," + z, "minecraft:air"));
            }
        });
    }

    private void breakBlock(UUID actor, int x, int y, int z, String block) {
        eventBus.publish(new BlockChangeEvent(actor, false, world, x, y, z,
                BlockChangeType.BREAK, block, "minecraft:air"));
    }

    @Test
    void recordsApprovedChangesButNeverCancelledOnes() {
        breakBlock(alice, 1, 64, 1, "minecraft:stone");
        // A cancelled (protected) change must never reach the log.
        BlockChangeEvent cancelled = new BlockChangeEvent(bob, false, world, 2, 64, 2,
                BlockChangeType.BREAK, "minecraft:diamond_block", "minecraft:air");
        cancelled.cancel();
        eventBus.publish(cancelled);

        List<RockWorldLogEntry> entries = service.query(LogQuery.builder().world(world).build()).join();

        assertEquals(1, entries.size());
        assertEquals(alice, entries.getFirst().actor());
        assertEquals("minecraft:stone", entries.getFirst().blockBefore());
    }

    @Test
    void queryFiltersByActorTimeAndRadius() {
        breakBlock(alice, 0, 64, 0, "minecraft:stone");
        breakBlock(bob, 10, 64, 10, "minecraft:dirt");
        breakBlock(bob, 200, 64, 200, "minecraft:sand");

        assertEquals(2, service.query(LogQuery.builder().world(world).actor(bob).build()).join().size());
        assertEquals(1, service.query(LogQuery.builder().world(world).around(10, 64, 10, 5).build()).join().size());
        assertEquals(0, service.query(LogQuery.builder().world(world)
                .until(Instant.now().minusSeconds(3600)).build()).join().size());
        assertEquals(3, service.query(LogQuery.builder().world(world)
                .since(Instant.now().minusSeconds(3600)).build()).join().size());
    }

    @Test
    void rollbackAppliesInverseAndIsRestorable() {
        registerMutator();
        breakBlock(bob, 5, 64, 5, "minecraft:stone");
        breakBlock(bob, 6, 64, 6, "minecraft:oak_log");

        int rolledBack = service.rollback(LogQuery.builder().world(world).actor(bob).build()).join();

        assertEquals(2, rolledBack);
        assertEquals("minecraft:stone", fakeWorld.get("5,64,5"));
        assertEquals("minecraft:oak_log", fakeWorld.get("6,64,6"));
        // Already-rolled-back entries are not rolled back twice.
        assertEquals(0, service.rollback(LogQuery.builder().world(world).actor(bob).build()).join());

        int restored = service.restore(LogQuery.builder().world(world).actor(bob).build()).join();
        assertEquals(2, restored);
        assertEquals("minecraft:air", fakeWorld.get("5,64,5"));
    }

    @Test
    void rollbackWithoutMutatorFailsLoudly() {
        breakBlock(bob, 5, 64, 5, "minecraft:stone");

        // requireMutator() fails fast, before any async work starts.
        assertThrows(IllegalStateException.class,
                () -> service.rollback(LogQuery.builder().world(world).build()));
    }

    @Test
    void consumerBatchesLargeBursts() {
        for (int i = 0; i < 500; i++) {
            breakBlock(alice, i, 64, 0, "minecraft:stone");
        }

        List<RockWorldLogEntry> entries = service.query(
                LogQuery.builder().world(world).limit(1000).build()).join();

        assertEquals(500, entries.size(), "burst of 500 changes fully persisted via batches");
    }
}
