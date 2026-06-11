package dev.rock.data.migration;

import dev.rock.api.annotations.RockInternal;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Administrative downgrade tool (Architectural Review D-4). Flyway OSS cannot
 * execute undo migrations, so every V###__name.sql ships a paired
 * U###__name.sql under db/undo, executed here.
 *
 * <p>Each undo runs in a single transaction: if it fails mid-execution the
 * transaction rolls back, the failure is logged, and the schema is not left
 * partially downgraded (TRS §5). Operators are expected to back up first.
 */
@RockInternal
@Singleton
public final class MigrationRollbackRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRollbackRunner.class);

    private final DataSource dataSource;

    @Inject
    public MigrationRollbackRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Rolls back a single migration version (e.g. 6 → executes U006 and removes
     * the matching flyway_schema_history row). Only the most recently applied
     * version may be rolled back — undoing a middle migration would leave the
     * schema history out of order and fail Flyway validation.
     */
    public void rollback(int version) {
        String paddedVersion = String.format("%03d", version);
        String script = readUndoScript(paddedVersion);

        int tip = appliedTip();
        if (version != tip) {
            throw new IllegalStateException(
                    "Only the latest applied migration (V" + String.format("%03d", tip)
                            + ") can be rolled back; requested V" + paddedVersion);
        }

        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String sql : script.split(";")) {
                    String trimmed = sql.trim();
                    if (!trimmed.isEmpty()) {
                        statement.execute(trimmed);
                    }
                }
                // Flyway records the version exactly as written in the script
                // name ("006"), but tolerate the unpadded form as well.
                statement.executeUpdate(
                        "DELETE FROM flyway_schema_history WHERE version IN ('"
                                + version + "', '" + paddedVersion + "')");
                connection.commit();
                log.info("Rolled back migration V{}", paddedVersion);
            } catch (SQLException e) {
                connection.rollback();
                log.error("Rollback of V{} failed; transaction rolled back, schema unchanged", paddedVersion, e);
                throw new IllegalStateException("Rollback of V" + paddedVersion + " failed", e);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not open connection for rollback", e);
        }
    }

    /** Highest applied versioned migration, from flyway_schema_history. */
    private int appliedTip() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var rs = statement.executeQuery(
                        "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL AND success = 1")) {
            int tip = -1;
            while (rs.next()) {
                tip = Math.max(tip, Integer.parseInt(rs.getString(1)));
            }
            if (tip < 0) {
                throw new IllegalStateException("No applied migrations to roll back");
            }
            return tip;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read flyway_schema_history", e);
        }
    }

    private String readUndoScript(String paddedVersion) {
        String prefix = "db/undo/U" + paddedVersion + "__";
        // Undo scripts follow the V-script names exactly; resolve by scanning
        // the known undo index file to stay classpath-portable.
        try (InputStream index = classpath("db/undo/index.txt")) {
            for (String line : new String(index.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String name = line.trim();
                if (name.startsWith("U" + paddedVersion + "__")) {
                    try (InputStream script = classpath("db/undo/" + name)) {
                        return new String(script.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed reading undo scripts", e);
        }
        throw new IllegalArgumentException("No undo script found matching " + prefix + "*");
    }

    private InputStream classpath(String resource) {
        InputStream stream = MigrationRollbackRunner.class.getClassLoader().getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("Missing classpath resource: " + resource);
        }
        return stream;
    }
}
