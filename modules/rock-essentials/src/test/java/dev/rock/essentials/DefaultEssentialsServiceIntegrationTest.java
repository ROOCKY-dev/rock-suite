package dev.rock.essentials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.rock.api.domain.RockLocation;
import dev.rock.api.services.PermissionService;
import dev.rock.core.service.DefaultServiceRegistry;
import dev.rock.data.jdbi.JdbiDataService;
import dev.rock.data.migration.DataMigrator;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Homes/warps/TPA over real SQLite; home limits come from permission options. */
class DefaultEssentialsServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;
    private JdbiDataService data;
    private DefaultServiceRegistry registry;
    private DefaultEssentialsService service;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID world = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + tempDir.resolve("essentials.db"));
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        new DataMigrator(dataSource).migrate();
        data = new JdbiDataService(Jdbi.create(dataSource));
        registry = new DefaultServiceRegistry();
        service = new DefaultEssentialsService(data, registry);
    }

    @AfterEach
    void tearDown() {
        data.onDisable();
        dataSource.close();
    }

    private RockLocation loc(double x, double y, double z) {
        return new RockLocation(world, x, y, z, 90f, 0f);
    }

    @Test
    void homesRoundTripWithYawPitch() {
        service.setHome(alice, "base", loc(100.5, 64, -20.25)).join();

        RockLocation home = service.home(alice, "base").join().orElseThrow();
        assertEquals(100.5, home.x());
        assertEquals(-20.25, home.z());
        assertEquals(90f, home.yaw());
        assertEquals(List.of("base"), service.homes(alice).join());

        service.deleteHome(alice, "base").join();
        assertTrue(service.home(alice, "base").join().isEmpty());
    }

    @Test
    void defaultHomeLimitIsEnforcedAndReplacingDoesNotCount() {
        service.setHome(alice, "one", loc(1, 64, 1)).join();
        service.setHome(alice, "two", loc(2, 64, 2)).join();
        service.setHome(alice, "three", loc(3, 64, 3)).join();

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> service.setHome(alice, "four", loc(4, 64, 4)).join());
        assertInstanceOf(IllegalStateException.class, thrown.getCause());

        // Overwriting an existing home is always allowed.
        service.setHome(alice, "three", loc(33, 64, 33)).join();
        assertEquals(33, service.home(alice, "three").join().orElseThrow().x());
    }

    @Test
    void permissionOptionRaisesTheHomeLimit() {
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.intOption(eq(alice), any())).thenReturn(OptionalInt.of(5));
        registry.register(PermissionService.class, permissions);

        for (int i = 1; i <= 5; i++) {
            service.setHome(alice, "home" + i, loc(i, 64, i)).join();
        }
        assertEquals(5, service.homes(alice).join().size());
        assertThrows(CompletionException.class,
                () -> service.setHome(alice, "home6", loc(6, 64, 6)).join());
    }

    @Test
    void warpsAreGlobal() {
        service.setWarp("spawn", loc(0, 70, 0), alice).join();
        service.setWarp("shop", loc(50, 70, 50), alice).join();

        assertEquals(List.of("shop", "spawn"), service.warps().join());
        assertEquals(0, service.warp("spawn").join().orElseThrow().x());

        service.deleteWarp("shop").join();
        assertEquals(List.of("spawn"), service.warps().join());
    }

    @Test
    void tpaFlowAcceptDenyAndExpiry() {
        service.tpa(alice, bob);
        assertEquals(alice, service.tpaccept(bob).orElseThrow(), "accept returns the requester");
        assertTrue(service.tpaccept(bob).isEmpty(), "request is consumed");

        service.tpa(alice, bob);
        assertEquals(alice, service.tpdeny(bob).orElseThrow());
        assertTrue(service.tpaccept(bob).isEmpty(), "denied request is gone");
    }
}
