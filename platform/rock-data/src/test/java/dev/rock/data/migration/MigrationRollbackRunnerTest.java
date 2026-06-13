package dev.rock.data.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationRollbackRunnerTest {

    /** Latest migration version in db/migration — bump when adding V###s. */
    private static final int TIP = 14;

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;
    private MigrationRollbackRunner rollback;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + tempDir.resolve("rollback.db"));
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        new DataMigrator(dataSource).migrate();
        rollback = new MigrationRollbackRunner(dataSource);
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    private List<String> tables() throws Exception {
        List<String> tables = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'rock_%'")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        return tables;
    }

    private int historyCount() throws Exception {
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE version IS NOT NULL")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private boolean worldLogHasSeqColumn() throws Exception {
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            s.executeQuery("SELECT seq FROM rock_world_log LIMIT 1").close();
            return true;
        } catch (java.sql.SQLException e) {
            return false;
        }
    }

    @Test
    void rollbackOfTipRemovesItsChangesAndHistoryRow() throws Exception {
        assertTrue(tables().contains("rock_web_accounts"));
        int before = historyCount();

        rollback.rollback(TIP);

        assertFalse(tables().contains("rock_web_accounts"), "V014 table dropped");
        assertEquals(before - 1, historyCount());
        // Earlier migrations untouched.
        assertTrue(tables().contains("rock_players"));
        assertTrue(tables().contains("rock_item_log"));
    }

    @Test
    void rollbackThenReMigrateReachesSameSchema() throws Exception {
        rollback.rollback(TIP);
        rollback.rollback(TIP - 1);
        assertFalse(worldLogHasSeqColumn(), "V013 seq column gone after rolling back to V012");

        new DataMigrator(dataSource).migrate();

        assertTrue(worldLogHasSeqColumn());
        assertTrue(tables().contains("rock_web_accounts"));
    }

    @Test
    void rollingBackANonTipVersionIsRejected() throws Exception {
        // Undoing a middle migration would corrupt Flyway's history order.
        assertThrows(IllegalStateException.class, () -> rollback.rollback(5));
        assertTrue(tables().contains("rock_metadata"), "schema must be untouched after rejection");
    }

    @Test
    void missingUndoScriptFailsLoudly() {
        assertThrows(IllegalArgumentException.class, () -> rollback.rollback(99));
    }
}
