package dev.rock.teams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.rock.api.domain.RockTeam;
import dev.rock.api.domain.TeamRole;
import dev.rock.api.event.EventBus;
import dev.rock.api.events.team.TeamCreatedEvent;
import dev.rock.api.events.team.TeamMemberJoinedEvent;
import dev.rock.core.event.DefaultEventBus;
import dev.rock.data.jdbi.JdbiDataService;
import dev.rock.data.migration.DataMigrator;
import dev.rock.teams.DefaultTeamService.TeamException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Full-stack team tests over real SQLite + DataService. */
class DefaultTeamServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;
    private JdbiDataService data;
    private EventBus eventBus;
    private DefaultTeamService service;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + tempDir.resolve("teams.db"));
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        new DataMigrator(dataSource).migrate();
        data = new JdbiDataService(Jdbi.create(dataSource));
        eventBus = new DefaultEventBus(Executors.newVirtualThreadPerTaskExecutor());
        service = new DefaultTeamService(data, eventBus);
        service.onEnable();
    }

    @AfterEach
    void tearDown() {
        service.onDisable();
        data.onDisable();
        dataSource.close();
    }

    @Test
    void createMakesFounderLeaderAndPublishes() {
        AtomicReference<TeamCreatedEvent> event = new AtomicReference<>();
        eventBus.subscribe(TeamCreatedEvent.class, event::set);

        RockTeam team = service.create("Rockers", alice).join();

        assertEquals("Rockers", team.name());
        assertEquals(alice, event.get().leader());
        assertEquals(TeamRole.LEADER, service.roleOfCached(team.id(), alice).orElseThrow());
        assertEquals(team.id(), service.teamOfCached(alice).orElseThrow().id());
    }

    @Test
    void teamNamesAreUnique() {
        service.create("Rockers", alice).join();

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> service.create("Rockers", bob).join());
        assertInstanceOf(TeamException.class, thrown.getCause());
    }

    @Test
    void onePlayerOneTeam() {
        RockTeam first = service.create("First", alice).join();
        RockTeam second = service.create("Second", bob).join();

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> service.addMember(second.id(), alice, TeamRole.MEMBER).join());
        assertInstanceOf(TeamException.class, thrown.getCause());

        // Leaving the first team frees the player up.
        service.removeMember(first.id(), alice).join();
        service.addMember(second.id(), alice, TeamRole.MEMBER).join();
        assertEquals(second.id(), service.teamOfCached(alice).orElseThrow().id());
    }

    @Test
    void membershipRoundTripsAndPublishes() {
        AtomicReference<TeamMemberJoinedEvent> joined = new AtomicReference<>();
        eventBus.subscribe(TeamMemberJoinedEvent.class, joined::set);
        RockTeam team = service.create("Rockers", alice).join();

        service.addMember(team.id(), bob, TeamRole.MEMBER).join();

        assertEquals(bob, joined.get().playerId());
        assertEquals(Map.of(alice, TeamRole.LEADER, bob, TeamRole.MEMBER),
                service.membersOf(team.id()).join());
    }

    @Test
    void disbandClearsMembershipAndSoftDeletes() {
        RockTeam team = service.create("Rockers", alice).join();
        service.addMember(team.id(), bob, TeamRole.OFFICER).join();

        service.disband(team.id()).join();

        assertTrue(service.teamOfCached(alice).isEmpty());
        assertTrue(service.teamOfCached(bob).isEmpty());
        assertTrue(service.findByName("Rockers").join().isEmpty(), "active lookup excludes disbanded");
        assertTrue(service.findById(team.id()).join().isPresent(), "soft delete keeps the row (DMS Rule 4)");
        // Name is reusable after disband.
        service.create("Rockers", alice).join();
    }

    @Test
    void cacheSurvivesRestart() {
        RockTeam team = service.create("Rockers", alice).join();
        service.addMember(team.id(), bob, TeamRole.OFFICER).join();

        DefaultTeamService restarted = new DefaultTeamService(data, eventBus);
        restarted.onEnable();

        assertEquals(TeamRole.OFFICER, restarted.roleOfCached(team.id(), bob).orElseThrow());
        assertEquals("Rockers", restarted.teamOfCached(alice).orElseThrow().name());
    }
}
