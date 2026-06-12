package dev.rock.claims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.rock.api.domain.ClaimType;
import dev.rock.api.domain.RockClaim;
import dev.rock.api.domain.bounds.ChunkBounds;
import dev.rock.api.domain.bounds.ChunkBounds.ChunkCoordinate;
import dev.rock.api.domain.owner.PlayerOwner;
import dev.rock.api.event.EventBus;
import dev.rock.api.event.EventPriority;
import dev.rock.api.events.claim.ClaimCreateEvent;
import dev.rock.api.events.claim.ClaimCreatedEvent;
import dev.rock.api.events.claim.ClaimDeletedEvent;
import dev.rock.api.events.claim.ClaimTransferredEvent;
import dev.rock.claims.DefaultClaimService.ClaimRejectedException;
import dev.rock.core.event.DefaultEventBus;
import dev.rock.data.jdbi.JdbiDataService;
import dev.rock.data.migration.DataMigrator;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Full-stack claims tests over real SQLite + DataService. */
class DefaultClaimServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;
    private JdbiDataService data;
    private EventBus eventBus;
    private DefaultClaimService service;

    private final UUID world = UUID.randomUUID();
    private final PlayerOwner owner = new PlayerOwner(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + tempDir.resolve("claims.db"));
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        new DataMigrator(dataSource).migrate();
        data = new JdbiDataService(Jdbi.create(dataSource));
        eventBus = new DefaultEventBus(Executors.newVirtualThreadPerTaskExecutor());
        service = new DefaultClaimService(new DataServiceClaimRepository(data), eventBus, new dev.rock.core.service.DefaultServiceRegistry());
    }

    @AfterEach
    void tearDown() {
        data.onDisable();
        dataSource.close();
    }

    private ChunkBounds bounds(int chunkX, int chunkZ) {
        return new ChunkBounds(world, Set.of(new ChunkCoordinate(chunkX, chunkZ)));
    }

    @Test
    void createPersistsAndPublishesCreatedEvent() {
        AtomicReference<ClaimCreatedEvent> created = new AtomicReference<>();
        eventBus.subscribe(ClaimCreatedEvent.class, created::set);

        RockClaim claim = service.create("MyBase", owner, ClaimType.PLAYER, bounds(0, 0)).join();

        assertEquals("MyBase", claim.displayName());
        assertEquals(claim.id(), created.get().claim().id());
        // Round-trips through DB including OwnerReference + bounds serialisation.
        RockClaim loaded = service.findById(claim.id()).join().orElseThrow();
        assertEquals(owner, loaded.owner());
        assertTrue(loaded.bounds().contains(8, 64, 8));
    }

    @Test
    void listenerAtFirstPriorityCanVetoCreation() {
        eventBus.subscribe(ClaimCreateEvent.class, EventPriority.FIRST, ClaimCreateEvent::cancel);

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> service.create("Vetoed", owner, ClaimType.PLAYER, bounds(0, 0)).join());

        assertInstanceOf(ClaimRejectedException.class, thrown.getCause());
        assertTrue(service.findByOwner(owner).join().isEmpty(), "vetoed claim must not persist");
    }

    @Test
    void overlappingClaimIsRejected() {
        service.create("First", owner, ClaimType.PLAYER, bounds(3, 3)).join();

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> service.create("Second", new PlayerOwner(UUID.randomUUID()),
                        ClaimType.PLAYER, bounds(3, 3)).join());

        assertInstanceOf(ClaimRejectedException.class, thrown.getCause());
    }

    @Test
    void claimAtFindsCoveringClaim() {
        RockClaim claim = service.create("Spawn", owner, ClaimType.ADMIN, bounds(2, 2)).join();

        Optional<RockClaim> found = service.claimAt(world, 2 * 16 + 5, 70, 2 * 16 + 5).join();
        Optional<RockClaim> missed = service.claimAt(world, 500, 70, 500).join();

        assertEquals(claim.id(), found.orElseThrow().id());
        assertTrue(missed.isEmpty());
    }

    @Test
    void transferChangesOwnerAndPublishes() {
        AtomicReference<ClaimTransferredEvent> event = new AtomicReference<>();
        eventBus.subscribe(ClaimTransferredEvent.class, event::set);
        RockClaim claim = service.create("Town", owner, ClaimType.TOWN, bounds(5, 5)).join();
        PlayerOwner newOwner = new PlayerOwner(UUID.randomUUID());

        RockClaim transferred = service.transfer(claim.id(), newOwner).join();

        assertEquals(newOwner, transferred.owner());
        assertEquals(owner, event.get().previousOwner());
    }

    @Test
    void deleteIsSoftAndFreesTheArea() {
        AtomicReference<ClaimDeletedEvent> event = new AtomicReference<>();
        eventBus.subscribe(ClaimDeletedEvent.class, event::set);
        RockClaim claim = service.create("Temp", owner, ClaimType.PLAYER, bounds(7, 7)).join();

        service.delete(claim.id()).join();

        assertNotNull(event.get());
        // Soft delete: still queryable by id (DMS Rule 4)…
        RockClaim deleted = service.findById(claim.id()).join().orElseThrow();
        assertNotNull(deleted.deletedAt());
        // …but the area is claimable again.
        RockClaim again = service.create("Replacement", owner, ClaimType.PLAYER, bounds(7, 7)).join();
        assertNotNull(again);
    }
}
