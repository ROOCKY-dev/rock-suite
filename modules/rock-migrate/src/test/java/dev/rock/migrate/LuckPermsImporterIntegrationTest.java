package dev.rock.migrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.rock.api.domain.ContextSet;
import dev.rock.api.domain.RockGroup;
import dev.rock.core.event.DefaultEventBus;
import dev.rock.data.jdbi.JdbiDataService;
import dev.rock.data.migration.DataMigrator;
import dev.rock.permissions.DataServicePermissionRepository;
import dev.rock.permissions.DefaultPermissionService;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Builds a real LuckPerms-schema SQLite file and imports it through the live permission stack. */
class LuckPermsImporterIntegrationTest {

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;
    private JdbiDataService data;
    private DefaultPermissionService permissions;
    private LuckPermsImporter importer;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private Path lpDb;

    @BeforeEach
    void setUp() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + tempDir.resolve("rock.db"));
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        new DataMigrator(dataSource).migrate();
        data = new JdbiDataService(Jdbi.create(dataSource));
        permissions = new DefaultPermissionService(new DataServicePermissionRepository(data),
                new DefaultEventBus(Executors.newVirtualThreadPerTaskExecutor()));
        permissions.onEnable();
        dev.rock.core.service.DefaultServiceRegistry registry = new dev.rock.core.service.DefaultServiceRegistry();
        registry.register(dev.rock.api.services.PermissionService.class, permissions);
        importer = new LuckPermsImporter(registry);

        lpDb = tempDir.resolve("luckperms-sqlite.db");
        try (Connection lp = DriverManager.getConnection("jdbc:sqlite:" + lpDb);
                Statement st = lp.createStatement()) {
            st.execute("CREATE TABLE luckperms_groups (name TEXT)");
            st.execute("""
                    CREATE TABLE luckperms_group_permissions
                        (name TEXT, permission TEXT, value INTEGER, server TEXT, world TEXT,
                         expiry INTEGER, contexts TEXT)
                    """);
            st.execute("""
                    CREATE TABLE luckperms_user_permissions
                        (uuid TEXT, permission TEXT, value INTEGER, server TEXT, world TEXT,
                         expiry INTEGER, contexts TEXT)
                    """);
            st.execute("CREATE TABLE luckperms_players (uuid TEXT, username TEXT, primary_group TEXT)");

            st.execute("INSERT INTO luckperms_groups VALUES ('admin'), ('default')");
            // admin: weight 10, a real node, prefix meta, a temporary node (skipped)
            st.execute("""
                    INSERT INTO luckperms_group_permissions VALUES
                        ('admin', 'weight.10', 1, 'global', 'global', 0, '{}'),
                        ('admin', 'rock.admin.*', 1, 'global', 'global', 0, '{}'),
                        ('admin', 'prefix.100.[Admin]', 1, 'global', 'global', 0, '{}'),
                        ('admin', 'some.temp.node', 1, 'global', 'global', 4102444800, '{}'),
                        ('default', 'rock.essentials.home', 1, 'global', 'global', 0, '{}')
                    """);
            // alice: admin member via group node, plus a nether-scoped grant
            // bob: a direct deny + a far-future temporary node
            st.execute("INSERT INTO luckperms_players VALUES ('" + alice + "', 'Alice', 'admin')");
            st.execute("INSERT INTO luckperms_players VALUES ('" + bob + "', 'Bob', 'default')");
            st.execute("""
                    INSERT INTO luckperms_user_permissions VALUES
                        ('%s', 'rock.claims.create', 1, 'global', 'nether', 0, '{}'),
                        ('%s', 'rock.economy.pay', 0, 'global', 'global', 0, '{}'),
                        ('%s', 'rock.event.vip', 1, 'global', 'global', 4102444800, '{}')
                    """.formatted(alice, bob, bob));
        }
    }

    @AfterEach
    void tearDown() {
        permissions.onDisable();
        data.onDisable();
        dataSource.close();
    }

    @Test
    void importsGroupsPermissionsContextsAndMemberships() {
        RmgImporter.ImportReport report = importer.run(lpDb).join();

        assertEquals(2, report.imported().get("groups"));
        assertTrue(report.imported().get("memberships") >= 2);
        // Weight 10 → priority 900; default (no weight) → 1000.
        List<RockGroup> aliceGroups = permissions.groupsOf(alice).join();
        assertEquals("admin", aliceGroups.getFirst().name());
        assertEquals(900, aliceGroups.getFirst().priority());

        // Group permission + meta arrived.
        assertTrue(permissions.has(alice, "rock.admin.modules"), "wildcard from admin group");
        assertEquals("[Admin]", permissions.option(alice, "prefix").orElseThrow());

        // Player nodes: context scoping + deny + temporary.
        assertFalse(permissions.has(alice, "rock.claims.create"), "nether-scoped grant inert globally");
        assertTrue(permissions.has(alice, "rock.claims.create", ContextSet.of("world", "nether")));
        assertFalse(permissions.has(bob, "rock.economy.pay"), "deny imported");
        assertTrue(permissions.has(bob, "rock.event.vip"), "far-future temporary node imported");

        // Temporary group node was skipped with a warning, not silently dropped.
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("some.temp.node")));
    }
}
