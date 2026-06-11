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

    @Test
    void rollbackRemovesTablesAndHistoryRow() throws Exception {
        assertTrue(tables().contains("rock_metadata"));
        int before = historyCount();

        rollback.rollback(6);

        assertFalse(tables().contains("rock_metadata"));
        assertFalse(tables().contains("rock_discord_links"));
        assertEquals(before - 1, historyCount());
        // Earlier migrations untouched.
        assertTrue(tables().contains("rock_players"));
    }

    @Test
    void rollbackThenReMigrateReachesSameSchema() throws Exception {
        rollback.rollback(6);
        rollback.rollback(5);
        assertFalse(tables().contains("rock_audit"));

        new DataMigrator(dataSource).migrate();

        assertTrue(tables().contains("rock_audit"));
        assertTrue(tables().contains("rock_metadata"));
    }

    @Test
    void missingUndoScriptFailsLoudly() {
        assertThrows(IllegalArgumentException.class, () -> rollback.rollback(99));
    }
}
