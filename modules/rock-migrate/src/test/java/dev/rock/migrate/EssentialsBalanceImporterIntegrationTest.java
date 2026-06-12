package dev.rock.migrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.rock.api.domain.owner.PlayerOwner;
import dev.rock.core.event.DefaultEventBus;
import dev.rock.data.jdbi.JdbiDataService;
import dev.rock.data.migration.DataMigrator;
import dev.rock.economy.DefaultEconomyService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** EssentialsX userdata import → SYSTEM-minted grant transactions. */
class EssentialsBalanceImporterIntegrationTest {

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;
    private JdbiDataService data;
    private DefaultEconomyService economy;
    private EssentialsBalanceImporter importer;

    private final UUID alice = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + tempDir.resolve("rock.db"));
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        new DataMigrator(dataSource).migrate();
        data = new JdbiDataService(Jdbi.create(dataSource));
        economy = new DefaultEconomyService(data,
                new DefaultEventBus(Executors.newVirtualThreadPerTaskExecutor()));
        dev.rock.core.service.DefaultServiceRegistry registry = new dev.rock.core.service.DefaultServiceRegistry();
        registry.register(dev.rock.api.services.EconomyService.class, economy);
        importer = new EssentialsBalanceImporter(registry);
    }

    @AfterEach
    void tearDown() {
        data.onDisable();
        dataSource.close();
    }

    @Test
    void importsBalancesAsAuditedGrants() throws Exception {
        Path userdata = tempDir.resolve("userdata");
        Files.createDirectories(userdata);
        Files.writeString(userdata.resolve(alice + ".yml"), """
                last-account-name: Alice
                money: '1234.56'
                timestamps:
                  login: 1700000000000
                """);
        Files.writeString(userdata.resolve("not-a-uuid.yml"), "money: '99'\n");

        RmgImporter.ImportReport report = importer.run(userdata).join();

        assertEquals(1, report.imported().get("balances"));
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("not-a-uuid")));

        var account = economy.findAccount(new PlayerOwner(alice)).join().orElseThrow();
        assertEquals(0, new BigDecimal("1234.56").compareTo(economy.balance(account.id()).join()));
        // The money arrived as a real SYSTEM transaction, not magic.
        var history = economy.history(account.id(), 5).join();
        assertEquals(1, history.size());
        assertEquals("EssentialsX import", history.getFirst().reason());
    }
}
