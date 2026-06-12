package dev.rock.claims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.rock.api.domain.ClaimRole;
import dev.rock.api.domain.ClaimType;
import dev.rock.api.domain.RockClaim;
import dev.rock.api.domain.RockTeam;
import dev.rock.api.domain.TeamRole;
import dev.rock.api.domain.bounds.ChunkBounds;
import dev.rock.api.domain.bounds.ChunkBounds.ChunkCoordinate;
import dev.rock.api.domain.owner.GroupOwner;
import dev.rock.api.event.EventBus;
import dev.rock.api.events.world.BlockChangeEvent;
import dev.rock.api.events.world.BlockChangeType;
import dev.rock.api.services.TeamService;
import dev.rock.core.event.DefaultEventBus;
import dev.rock.core.service.DefaultServiceRegistry;
import dev.rock.data.jdbi.JdbiDataService;
import dev.rock.data.migration.DataMigrator;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The cross-module showcase: a team-owned claim resolves trust through
 * TeamService via the ServiceRegistry — three incumbent mods' worth of
 * integration with zero glue code.
 */
class TeamOwnedClaimIntegrationTest {

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;
    private JdbiDataService data;
    private EventBus eventBus;
    private dev.rock.teams.DefaultTeamService teams;
    private DefaultClaimService claims;
    private ClaimProtectionListener protection;

    private final UUID world = UUID.randomUUID();
    private final UUID leader = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + tempDir.resolve("teamclaims.db"));
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        new DataMigrator(dataSource).migrate();
        data = new JdbiDataService(Jdbi.create(dataSource));
        eventBus = new DefaultEventBus(Executors.newVirtualThreadPerTaskExecutor());

        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        teams = new dev.rock.teams.DefaultTeamService(data, eventBus);
        teams.onEnable();
        registry.register(TeamService.class, teams);

        claims = new DefaultClaimService(new DataServiceClaimRepository(data), eventBus, registry);
        claims.onEnable();
        protection = new ClaimProtectionListener(claims, eventBus);
        protection.onEnable();
    }

    @AfterEach
    void tearDown() {
        protection.onDisable();
        claims.onDisable();
        teams.onDisable();
        data.onDisable();
        dataSource.close();
    }

    private boolean breakAllowed(UUID actor, int x, int z) {
        BlockChangeEvent event = eventBus.publish(new BlockChangeEvent(
                actor, false, world, x, 64, z, BlockChangeType.BREAK, "minecraft:stone", "minecraft:air"));
        return !event.cancelled();
    }

    @Test
    void teamMembershipMapsOntoClaimTrust() {
        RockTeam team = teams.create("Rockers", leader).join();
        teams.addMember(team.id(), member, TeamRole.MEMBER).join();

        RockClaim base = claims.create("Team Base", new GroupOwner(team.id()), ClaimType.TOWN,
                new ChunkBounds(world, Set.of(new ChunkCoordinate(0, 0)))).join();

        assertEquals(ClaimRole.MANAGER, claims.effectiveRole(base, leader).orElseThrow(),
                "leader manages the team claim");
        assertEquals(ClaimRole.BUILD, claims.effectiveRole(base, member).orElseThrow(),
                "member builds in the team claim");
        assertTrue(claims.effectiveRole(base, outsider).isEmpty());

        assertTrue(breakAllowed(leader, 5, 5));
        assertTrue(breakAllowed(member, 5, 5));
        assertFalse(breakAllowed(outsider, 5, 5), "outsider blocked from team land");
    }

    @Test
    void leavingTheTeamRevokesClaimAccess() {
        RockTeam team = teams.create("Rockers", leader).join();
        teams.addMember(team.id(), member, TeamRole.MEMBER).join();
        RockClaim base = claims.create("Team Base", new GroupOwner(team.id()), ClaimType.TOWN,
                new ChunkBounds(world, Set.of(new ChunkCoordinate(0, 0)))).join();
        assertTrue(breakAllowed(member, 5, 5));

        teams.removeMember(team.id(), member).join();

        assertFalse(breakAllowed(member, 5, 5), "access revoked the moment membership ends");
        // Explicit per-claim trust still works for non-members.
        claims.trust(base.id(), outsider, ClaimRole.BUILD).join();
        assertTrue(breakAllowed(outsider, 5, 5));
    }
}
