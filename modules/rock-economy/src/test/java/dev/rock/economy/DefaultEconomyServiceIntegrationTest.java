package dev.rock.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.rock.api.domain.AccountType;
import dev.rock.api.domain.RockEconomyAccount;
import dev.rock.api.domain.RockTransaction;
import dev.rock.api.domain.TransactionStatus;
import dev.rock.api.domain.owner.PlayerOwner;
import dev.rock.api.domain.owner.SystemOwner;
import dev.rock.api.event.EventBus;
import dev.rock.api.event.EventPriority;
import dev.rock.api.events.economy.TransactionCreatedEvent;
import dev.rock.api.events.economy.TransactionEvent;
import dev.rock.api.events.economy.TransactionFailedEvent;
import dev.rock.core.event.DefaultEventBus;
import dev.rock.data.jdbi.JdbiDataService;
import dev.rock.data.migration.DataMigrator;
import java.math.BigDecimal;
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

/** Full-stack economy tests over real SQLite + DataService. */
class DefaultEconomyServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;
    private JdbiDataService data;
    private EventBus eventBus;
    private DefaultEconomyService service;

    private final PlayerOwner alice = new PlayerOwner(UUID.randomUUID());
    private final PlayerOwner bob = new PlayerOwner(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + tempDir.resolve("eco.db"));
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        new DataMigrator(dataSource).migrate();
        data = new JdbiDataService(Jdbi.create(dataSource));
        eventBus = new DefaultEventBus(Executors.newVirtualThreadPerTaskExecutor());
        service = new DefaultEconomyService(data, eventBus);
    }

    @AfterEach
    void tearDown() {
        data.onDisable();
        dataSource.close();
    }

    private RockEconomyAccount fund(PlayerOwner owner, String amount) {
        RockEconomyAccount account = service.openAccount(owner, AccountType.PLAYER).join();
        // Seed funds from the system treasury.
        RockEconomyAccount treasury = service.openAccount(SystemOwner.server(), AccountType.SYSTEM).join();
        data.update("UPDATE rock_accounts SET balance = :b WHERE id = :id",
                java.util.Map.of("b", new BigDecimal(amount), "id", account.id().toString())).join();
        return account;
    }

    @Test
    void openAccountIsIdempotentPerOwner() {
        RockEconomyAccount first = service.openAccount(alice, AccountType.PLAYER).join();
        RockEconomyAccount second = service.openAccount(alice, AccountType.PLAYER).join();

        assertEquals(first.id(), second.id());
        assertEquals(BigDecimal.ZERO.compareTo(service.balance(first.id()).join()), 0);
    }

    @Test
    void successfulTransferMovesMoneyAtomicallyAndPublishes() {
        RockEconomyAccount from = fund(alice, "100.00");
        RockEconomyAccount to = service.openAccount(bob, AccountType.PLAYER).join();
        AtomicReference<TransactionCreatedEvent> created = new AtomicReference<>();
        eventBus.subscribe(TransactionCreatedEvent.class, created::set);

        RockTransaction tx = service.transfer(alice, bob, new BigDecimal("40.00"), "test payment").join();

        assertEquals(TransactionStatus.COMPLETED, tx.status());
        assertEquals(0, new BigDecimal("60.00").compareTo(service.balance(from.id()).join()));
        assertEquals(0, new BigDecimal("40.00").compareTo(service.balance(to.id()).join()));
        assertEquals(tx.id(), created.get().transaction().id());
    }

    @Test
    void insufficientFundsFailsWithoutTouchingBalances() {
        RockEconomyAccount from = fund(alice, "10.00");
        RockEconomyAccount to = service.openAccount(bob, AccountType.PLAYER).join();
        AtomicReference<TransactionFailedEvent> failed = new AtomicReference<>();
        eventBus.subscribe(TransactionFailedEvent.class, failed::set);

        RockTransaction tx = service.transfer(alice, bob, new BigDecimal("50.00"), "too much").join();

        assertEquals(TransactionStatus.FAILED, tx.status());
        assertEquals("Insufficient funds", failed.get().failureReason());
        assertEquals(0, new BigDecimal("10.00").compareTo(service.balance(from.id()).join()));
        assertEquals(0, BigDecimal.ZERO.compareTo(service.balance(to.id()).join()));
    }

    @Test
    void listenerCanVetoTransaction() {
        fund(alice, "100.00");
        service.openAccount(bob, AccountType.PLAYER).join();
        eventBus.subscribe(TransactionEvent.class, EventPriority.FIRST, TransactionEvent::cancel);

        RockTransaction tx = service.transfer(alice, bob, new BigDecimal("5.00"), "vetoed").join();

        assertEquals(TransactionStatus.FAILED, tx.status());
    }

    @Test
    void reversalRestoresBalancesAndLinksToOriginal() {
        RockEconomyAccount from = fund(alice, "100.00");
        RockEconomyAccount to = service.openAccount(bob, AccountType.PLAYER).join();
        RockTransaction original = service.transfer(alice, bob, new BigDecimal("30.00"), "oops").join();

        RockTransaction reversal = service.reverse(original.id(), "refund").join();

        assertEquals(original.id(), reversal.reversalOf());
        assertEquals(0, new BigDecimal("100.00").compareTo(service.balance(from.id()).join()));
        assertEquals(0, BigDecimal.ZERO.compareTo(service.balance(to.id()).join()));
        // Original is now marked REVERSED in history.
        List<RockTransaction> history = service.history(from.id(), 10).join();
        assertTrue(history.stream().anyMatch(
                t -> t.id().equals(original.id()) && t.status() == TransactionStatus.REVERSED));
    }

    @Test
    void historyReturnsBothDirections() {
        RockEconomyAccount account = fund(alice, "100.00");
        service.openAccount(bob, AccountType.PLAYER).join();
        service.transfer(alice, bob, new BigDecimal("10.00"), "one").join();
        service.transfer(alice, bob, new BigDecimal("5.00"), "two").join();

        List<RockTransaction> history = service.history(account.id(), 10).join();

        assertEquals(2, history.size());
        assertNotNull(history.get(0).reason());
    }
}
