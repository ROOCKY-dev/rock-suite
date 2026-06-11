package dev.rock.claims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.rock.api.domain.ClaimRole;
import dev.rock.api.domain.ClaimType;
import dev.rock.api.domain.RockClaim;
import dev.rock.api.domain.bounds.ChunkBounds;
import dev.rock.api.domain.bounds.ChunkBounds.ChunkCoordinate;
import dev.rock.api.domain.owner.PlayerOwner;
import dev.rock.api.event.EventBus;
import dev.rock.api.events.world.BlockChangeEvent;
import dev.rock.api.events.world.BlockChangeType;
import dev.rock.api.events.world.InteractionType;
import dev.rock.api.events.world.PlayerInteractEvent;
import dev.rock.core.event.DefaultEventBus;
import dev.rock.data.jdbi.JdbiDataService;
import dev.rock.data.migration.DataMigrator;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Trust roles + protection enforcement over real SQLite + the event layer. */
class ClaimProtectionIntegrationTest {

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;
    private JdbiDataService data;
    private EventBus eventBus;
    private DefaultClaimService service;
    private ClaimProtectionListener protection;

    private final UUID world = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private RockClaim claim;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + tempDir.resolve("protect.db"));
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        new DataMigrator(dataSource).migrate();
        data = new JdbiDataService(Jdbi.create(dataSource));
        eventBus = new DefaultEventBus(Executors.newVirtualThreadPerTaskExecutor());
        service = new DefaultClaimService(new DataServiceClaimRepository(data), eventBus);
        service.onEnable();
        protection = new ClaimProtectionListener(service, eventBus);
        protection.onEnable();

        claim = service.create("Base", new PlayerOwner(alice), ClaimType.PLAYER,
                new ChunkBounds(world, Set.of(new ChunkCoordinate(0, 0)))).join();
    }

    @AfterEach
    void tearDown() {
        protection.onDisable();
        service.onDisable();
        data.onDisable();
        dataSource.close();
    }

    private boolean breakAllowed(UUID actor, boolean fake, int x, int z) {
        BlockChangeEvent event = eventBus.publish(new BlockChangeEvent(
                actor, fake, world, x, 64, z, BlockChangeType.BREAK, "minecraft:stone", "minecraft:air"));
        return !event.cancelled();
    }

    private boolean interactAllowed(UUID actor, InteractionType type, int x, int z) {
        PlayerInteractEvent event = eventBus.publish(new PlayerInteractEvent(
                actor, false, world, x, 64, z, type, "minecraft:chest"));
        return !event.cancelled();
    }

    @Test
    void ownerMayBuildStrangerMayNot() {
        assertTrue(breakAllowed(alice, false, 5, 5), "owner builds in own claim");
        assertFalse(breakAllowed(bob, false, 5, 5), "stranger is blocked");
        assertTrue(breakAllowed(bob, false, 500, 500), "wilderness unaffected");
    }

    @Test
    void trustRolesGateByLevel() {
        service.trust(claim.id(), bob, ClaimRole.ACCESS).join();
        assertFalse(breakAllowed(bob, false, 5, 5), "ACCESS cannot build");
        assertFalse(interactAllowed(bob, InteractionType.OPEN_CONTAINER, 5, 5), "ACCESS cannot open containers");
        assertTrue(interactAllowed(bob, InteractionType.USE_BLOCK, 5, 5), "ACCESS can use doors/buttons");

        service.trust(claim.id(), bob, ClaimRole.CONTAINER).join();
        assertTrue(interactAllowed(bob, InteractionType.OPEN_CONTAINER, 5, 5), "CONTAINER opens chests");
        assertFalse(breakAllowed(bob, false, 5, 5), "CONTAINER still cannot build");

        service.trust(claim.id(), bob, ClaimRole.BUILD).join();
        assertTrue(breakAllowed(bob, false, 5, 5), "BUILD can break/place");

        service.untrust(claim.id(), bob).join();
        assertFalse(breakAllowed(bob, false, 5, 5), "untrust revokes immediately");
    }

    @Test
    void fakePlayersAndActorlessChangesAreDeniedInClaims() {
        assertFalse(breakAllowed(bob, true, 5, 5), "fake player (machine) denied");
        assertFalse(breakAllowed(null, false, 5, 5), "actor-less (mob/environment) denied");
        assertTrue(breakAllowed(null, false, 500, 500), "actor-less in wilderness fine");
    }

    @Test
    void membersSurviveCacheRebuild() {
        service.trust(claim.id(), bob, ClaimRole.BUILD).join();

        // Simulate a restart: fresh service instance warms its index from storage.
        DefaultClaimService restarted =
                new DefaultClaimService(new DataServiceClaimRepository(data), eventBus);
        restarted.onEnable();

        RockClaim cached = restarted.claimAtCached(world, 5, 64, 5).orElseThrow();
        assertEquals(ClaimRole.BUILD, restarted.effectiveRole(cached, bob).orElseThrow());
        assertEquals(Map.of(bob, ClaimRole.BUILD), restarted.membersOf(claim.id()).join());
    }
}
