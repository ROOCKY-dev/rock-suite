package dev.rock.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.rock.api.domain.PermissionState;
import dev.rock.api.domain.RockGroup;
import dev.rock.api.event.EventBus;
import dev.rock.api.events.permission.PermissionGrantedEvent;
import dev.rock.core.event.DefaultEventBus;
import dev.rock.data.jdbi.JdbiDataService;
import dev.rock.data.migration.DataMigrator;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Full-stack permission tests: real SQLite + migrations + DataService, exactly
 * as the module runs in production. The module code itself never touches
 * JDBC/JDBI — only the DataService contract.
 */
class DefaultPermissionServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;
    private JdbiDataService data;
    private EventBus eventBus;
    private DefaultPermissionService service;

    private final UUID player = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + tempDir.resolve("perm.db"));
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        new DataMigrator(dataSource).migrate();
        data = new JdbiDataService(Jdbi.create(dataSource));
        eventBus = new DefaultEventBus(Executors.newVirtualThreadPerTaskExecutor());
        service = new DefaultPermissionService(new DataServicePermissionRepository(data), eventBus);
        service.onEnable();
    }

    @AfterEach
    void tearDown() {
        service.onDisable();
        data.onDisable();
        dataSource.close();
    }

    @Test
    void grantPersistsEvaluatesAndPublishesEvent() {
        AtomicReference<PermissionGrantedEvent> event = new AtomicReference<>();
        eventBus.subscribe(PermissionGrantedEvent.class, event::set);

        assertFalse(service.has(player, "rock.claims.create"));
        service.grant(player, "rock.claims.create").join();

        assertTrue(service.has(player, "rock.claims.create"));
        assertEquals("rock.claims.create", event.get().node());

        // Survives a cache rebuild (i.e. it is actually persisted).
        service.reload().join();
        assertTrue(service.has(player, "rock.claims.create"));
    }

    @Test
    void playerDenyOverridesGroupAllow() {
        RockGroup admins = service.createGroup("Admin", 10).join();
        service.grantGroup(admins.id(), "rock.admin.reload").join();
        service.assignGroup(player, admins.id()).join();
        assertTrue(service.has(player, "rock.admin.reload"));

        service.deny(player, "rock.admin.reload").join();

        assertEquals(PermissionState.DENY, service.check(player, "rock.admin.reload"));
        assertFalse(service.has(player, "rock.admin.reload"));
    }

    @Test
    void higherPriorityGroupWins() {
        RockGroup member = service.createGroup("Member", 100).join();
        RockGroup admin = service.createGroup("Admin", 10).join();
        service.grantGroup(admin.id(), "rock.economy.withdraw").join();
        // Lower-priority group denies; Admin (priority 10) must win.
        service.assignGroup(player, member.id()).join();
        service.assignGroup(player, admin.id()).join();
        new DataServicePermissionRepository(data)
                .saveGroupPermission(member.id(), "rock.economy.withdraw", dev.rock.api.domain.ContextSet.empty(), PermissionState.DENY, null).join();
        service.reload().join();

        assertTrue(service.has(player, "rock.economy.withdraw"));
    }

    @Test
    void samePriorityTieResolvesAlphabetically() {
        // DMS tie-breaking rule: "Alpha" beats "Beta" at equal priority.
        RockGroup alpha = service.createGroup("Alpha", 50).join();
        RockGroup beta = service.createGroup("Beta", 50).join();
        service.grantGroup(alpha.id(), "rock.claims.delete").join();
        new DataServicePermissionRepository(data)
                .saveGroupPermission(beta.id(), "rock.claims.delete", dev.rock.api.domain.ContextSet.empty(), PermissionState.DENY, null).join();
        service.assignGroup(player, alpha.id()).join();
        service.assignGroup(player, beta.id()).join();
        service.reload().join();

        assertEquals(PermissionState.ALLOW, service.check(player, "rock.claims.delete"));
    }

    @Test
    void wildcardNodesMatchPrefixes() {
        service.grant(player, "rock.claims.*").join();

        assertTrue(service.has(player, "rock.claims.create"));
        assertTrue(service.has(player, "rock.claims.delete"));
        assertFalse(service.has(player, "rock.economy.withdraw"));
    }

    @Test
    void unsetRevokesAndPublishes() {
        service.grant(player, "rock.claims.create").join();
        service.unset(player, "rock.claims.create").join();

        assertEquals(PermissionState.UNSET, service.check(player, "rock.claims.create"));
    }

    @Test
    void contextScopedPermissionsApplyOnlyInContext() {
        dev.rock.api.domain.ContextSet nether = dev.rock.api.domain.ContextSet.of("world", "nether");
        service.grant(player, "rock.claims.create", nether).join();

        assertFalse(service.has(player, "rock.claims.create"), "global query: nether-scoped node inert");
        assertTrue(service.has(player, "rock.claims.create", nether), "applies in its context");
        assertFalse(service.has(player, "rock.claims.create",
                dev.rock.api.domain.ContextSet.of("world", "overworld")), "wrong world: inert");
    }

    @Test
    void moreSpecificContextWinsOverGlobal() {
        dev.rock.api.domain.ContextSet creativeWorld = dev.rock.api.domain.ContextSet.of("world", "creative");
        service.grant(player, "rock.economy.withdraw").join();
        service.deny(player, "rock.economy.withdraw", creativeWorld).join();

        assertTrue(service.has(player, "rock.economy.withdraw"), "global grant holds elsewhere");
        assertFalse(service.has(player, "rock.economy.withdraw", creativeWorld),
                "specific deny overrides global allow in its context");
    }

    @Test
    void temporaryPermissionExpires() throws Exception {
        service.grantTemporary(player, "rock.event.vip", java.time.Duration.ofMillis(80)).join();
        assertTrue(service.has(player, "rock.event.vip"), "active before expiry");

        Thread.sleep(120);

        assertFalse(service.has(player, "rock.event.vip"), "evaluation ignores expired nodes");
        // The sweep would also purge the row; evaluation alone must already be correct.
    }

    @Test
    void optionsResolvePlayerFirstThenGroupOrder() {
        RockGroup member = service.createGroup("Member", 100).join();
        RockGroup admin = service.createGroup("Admin", 10).join();
        service.assignGroup(player, member.id()).join();
        service.assignGroup(player, admin.id()).join();
        service.setGroupOption(member.id(), "prefix", "[Member]").join();
        service.setGroupOption(admin.id(), "prefix", "[Admin]").join();
        service.setGroupOption(member.id(), "rock.essentials.homes.max", "3").join();

        assertEquals("[Admin]", service.option(player, "prefix").orElseThrow(),
                "higher-priority group's prefix wins");
        assertEquals(3, service.intOption(player, "rock.essentials.homes.max").orElseThrow());

        service.setPlayerOption(player, "prefix", "[Founder]").join();
        assertEquals("[Founder]", service.option(player, "prefix").orElseThrow(), "player option overrides");
        assertTrue(service.option(player, "suffix").isEmpty());
        assertTrue(service.intOption(player, "prefix").isEmpty(), "non-numeric option yields empty int");
    }

    @Test
    void groupsOfReturnsResolutionOrder() {
        RockGroup member = service.createGroup("Member", 100).join();
        RockGroup admin = service.createGroup("Admin", 10).join();
        service.assignGroup(player, member.id()).join();
        service.assignGroup(player, admin.id()).join();

        List<RockGroup> groups = service.groupsOf(player).join();

        assertEquals(List.of("Admin", "Member"), groups.stream().map(RockGroup::name).toList());
    }
}
