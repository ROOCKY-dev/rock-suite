package dev.rock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.rock.api.data.DataService;
import dev.rock.data.jdbi.JdbiDataService;
import dev.rock.data.migration.DataMigrator;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Real-database integration tests: SQLite file + Flyway migrations + JDBI,
 * exactly as deployed by default (TRS §5 Tier 1).
 */
class JdbiDataServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;
    private JdbiDataService dataService;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + tempDir.resolve("test.db"));
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        new DataMigrator(dataSource).migrate();
        dataService = new JdbiDataService(Jdbi.create(dataSource));
    }

    @AfterEach
    void tearDown() {
        dataService.onDisable();
        dataSource.close();
    }

    private Map<String, Object> playerParams(UUID id, String username, Instant now) {
        return Map.of(
                "id", id.toString(),
                "username", username,
                "locale", "en",
                "first_join", now.toEpochMilli(),
                "last_seen", now.toEpochMilli(),
                "status", "ACTIVE");
    }

    private static final String INSERT_PLAYER = """
            INSERT INTO rock_players (id, username, preferred_locale, first_join, last_seen, status, deleted_at)
            VALUES (:id, :username, :locale, :first_join, :last_seen, :status, NULL)
            """;

    @Test
    void insertAndQueryRoundTripsThroughRowView() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        int rows = dataService.update(INSERT_PLAYER, playerParams(id, "Ahmed", now)).get();
        assertEquals(1, rows);

        Optional<PlayerRow> found = dataService.queryOne(
                "SELECT * FROM rock_players WHERE id = :id",
                Map.of("id", id.toString()),
                row -> new PlayerRow(row.getUuid("id"), row.getString("username"),
                        row.getInstant("first_join"), row.getInstant("deleted_at"))).get();

        assertTrue(found.isPresent());
        assertEquals(id, found.get().id());
        assertEquals("Ahmed", found.get().username());
        assertEquals(now.toEpochMilli(), found.get().firstJoin().toEpochMilli());
        assertEquals(null, found.get().deletedAt()); // SQL NULL → null Instant
    }

    record PlayerRow(UUID id, String username, Instant firstJoin, Instant deletedAt) {
    }

    @Test
    void batchInsertsAllRows() throws Exception {
        Instant now = Instant.now();
        List<Map<String, Object>> batch = List.of(
                playerParams(UUID.randomUUID(), "a", now),
                playerParams(UUID.randomUUID(), "b", now),
                playerParams(UUID.randomUUID(), "c", now));

        int[] results = dataService.batch(INSERT_PLAYER, batch).get();

        assertEquals(3, results.length);
        List<String> names = dataService.query(
                "SELECT username FROM rock_players ORDER BY username",
                Map.of(), row -> row.getString("username")).get();
        assertEquals(List.of("a", "b", "c"), names);
    }

    @Test
    void transactionRollsBackOnFailure() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        ExecutionException thrown = assertThrows(ExecutionException.class, () ->
                dataService.inTransaction(tx -> {
                    tx.update(INSERT_PLAYER, playerParams(id, "ghost", now));
                    throw new IllegalStateException("force rollback");
                }).get());
        assertTrue(thrown.getCause() instanceof IllegalStateException
                || thrown.getCause().getCause() instanceof IllegalStateException);

        long count = dataService.queryOne(
                "SELECT COUNT(*) AS c FROM rock_players WHERE id = :id",
                Map.of("id", id.toString()), row -> row.getLong("c")).get().orElseThrow();
        assertEquals(0, count, "insert must have been rolled back");
    }

    @Test
    void transactionCommitsAtomically() throws Exception {
        Instant now = Instant.now();

        String result = dataService.inTransaction(tx -> {
            tx.update(INSERT_PLAYER, playerParams(UUID.randomUUID(), "tx-player", now));
            return tx.queryOne("SELECT username FROM rock_players WHERE username = :u",
                    Map.of("u", "tx-player"), row -> row.getString("username")).orElseThrow();
        }).get();

        assertEquals("tx-player", result);
    }

    @Test
    void databaseWorkRunsOffTheCallingThread() throws Exception {
        Thread caller = Thread.currentThread();

        Boolean differentThread = dataService.queryOne("SELECT 1 AS one", Map.of(),
                row -> Thread.currentThread() != caller).get().orElseThrow();

        assertTrue(differentThread, "DataService must never run on the calling (game) thread");
    }

    @Test
    void allCoreTablesExistAfterMigration() throws Exception {
        List<String> tables = dataService.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'rock_%' ORDER BY name",
                Map.of(), row -> row.getString("name")).get();

        assertTrue(tables.containsAll(List.of(
                "rock_players", "rock_groups", "rock_group_permissions", "rock_player_permissions",
                "rock_player_groups", "rock_claims", "rock_accounts", "rock_transactions",
                "rock_punishments", "rock_audit", "rock_discord_links", "rock_metadata")), tables.toString());
    }
}
