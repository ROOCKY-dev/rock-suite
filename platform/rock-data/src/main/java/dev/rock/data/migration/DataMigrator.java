package dev.rock.data.migration;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.lifecycle.LifecycleAware;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs versioned forward migrations on platform enable (TRS §5). Flyway OSS is
 * forward-only; downgrade is handled by {@link MigrationRollbackRunner}
 * (Architectural Review D-4).
 */
@RockInternal
@Singleton
public final class DataMigrator implements LifecycleAware {

    public static final String MIGRATION_LOCATION = "classpath:db/migration";
    private static final String MIGRATION_INDEX = "db/migration/index.txt";

    private static final Logger log = LoggerFactory.getLogger(DataMigrator.class);

    private final DataSource dataSource;

    @Inject
    public DataMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void migrate() {
        Flyway flyway = Flyway.configure(DataMigrator.class.getClassLoader())
                .dataSource(dataSource)
                .locations(resolveLocation())
                .load();
        var result = flyway.migrate();
        log.info("Database schema at version {} ({} migration(s) applied)",
                result.targetSchemaVersion, result.migrationsExecuted);
    }

    /**
     * Resolve the Flyway migration location in a classloader-portable way.
     *
     * <p>Flyway's classpath scanner cannot enumerate resources inside
     * module-isolating classloaders (e.g. NeoForge's jar-in-module layer), so
     * it silently finds zero migrations there. Instead we read the committed
     * {@code db/migration/index.txt} (a single resource, always loadable by name
     * via {@code getResourceAsStream} on any classloader — the same trick
     * {@link MigrationRollbackRunner} uses for undo scripts), extract the listed
     * scripts to a temp directory, and point Flyway at its rock-solid
     * filesystem scanner. The SQL content is byte-identical to the classpath
     * copies, so recorded checksums stay portable across loaders.
     */
    private String resolveLocation() {
        ClassLoader cl = DataMigrator.class.getClassLoader();
        try (InputStream index = cl.getResourceAsStream(MIGRATION_INDEX)) {
            if (index == null) {
                // Dev/test fallback (flat classloader): scan the classpath.
                return MIGRATION_LOCATION;
            }
            Path dir = Files.createTempDirectory("rock-migrations");
            dir.toFile().deleteOnExit();
            String[] names = new String(index.readAllBytes(), StandardCharsets.UTF_8).split("\n");
            for (String raw : names) {
                String name = raw.trim();
                if (name.isEmpty()) {
                    continue;
                }
                try (InputStream sql = cl.getResourceAsStream("db/migration/" + name)) {
                    if (sql == null) {
                        throw new IllegalStateException("indexed migration missing from classpath: " + name);
                    }
                    Path out = dir.resolve(name);
                    Files.copy(sql, out);
                    out.toFile().deleteOnExit();
                }
            }
            return "filesystem:" + dir.toAbsolutePath();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to stage migrations for Flyway", e);
        }
    }

    @Override
    public void onEnable() {
        migrate();
    }

    @Override
    public void onDisable() {
    }
}
