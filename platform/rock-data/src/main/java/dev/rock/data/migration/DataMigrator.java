package dev.rock.data.migration;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.lifecycle.LifecycleAware;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
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

    private static final Logger log = LoggerFactory.getLogger(DataMigrator.class);

    private final DataSource dataSource;

    @Inject
    public DataMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void migrate() {
        Flyway flyway = Flyway.configure(DataMigrator.class.getClassLoader())
                .dataSource(dataSource)
                .locations(MIGRATION_LOCATION)
                .load();
        var result = flyway.migrate();
        log.info("Database schema at version {} ({} migration(s) applied)",
                result.targetSchemaVersion, result.migrationsExecuted);
    }

    @Override
    public void onEnable() {
        migrate();
    }

    @Override
    public void onDisable() {
    }
}
