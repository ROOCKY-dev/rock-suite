package dev.rock.data;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.rock.api.data.DataService;
import dev.rock.api.services.AuditService;
import dev.rock.api.services.PlayerService;
import dev.rock.data.audit.DefaultAuditService;
import dev.rock.data.jdbi.JdbiDataService;
import dev.rock.data.player.DefaultPlayerService;
import dev.rock.data.migration.DataMigrator;
import dev.rock.data.migration.MigrationRollbackRunner;
import jakarta.inject.Singleton;
import java.util.Objects;
import javax.sql.DataSource;
import org.jdbi.v3.core.Jdbi;

/**
 * rock-data's contributed platform module (Architectural Review D-1): the
 * loader adapter passes this to RockPlatform.boot() alongside RockCoreModule.
 * rock-core never references this class — the documented cycle is gone.
 */
public final class RockDataModule extends AbstractModule {

    private final DatabaseSettings settings;

    public RockDataModule(DatabaseSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    protected void configure() {
        bind(DatabaseSettings.class).toInstance(settings);
        bind(DataService.class).to(JdbiDataService.class).in(Scopes.SINGLETON);
        bind(JdbiDataService.class).in(Scopes.SINGLETON);
        bind(DataMigrator.class).in(Scopes.SINGLETON);
        bind(MigrationRollbackRunner.class).in(Scopes.SINGLETON);
        bind(PlayerService.class).to(DefaultPlayerService.class).in(Scopes.SINGLETON);
        bind(DefaultPlayerService.class).in(Scopes.SINGLETON);
        bind(AuditService.class).to(DefaultAuditService.class).in(Scopes.SINGLETON);
        bind(DefaultAuditService.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    HikariDataSource provideDataSource(DatabaseSettings settings) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("rock-pool");
        hikari.setJdbcUrl(settings.jdbcUrl());
        if (settings.username() != null) {
            hikari.setUsername(settings.username());
        }
        if (settings.password() != null) {
            hikari.setPassword(settings.password());
        }
        // SQLite is a single-writer engine; more than one pooled connection
        // produces SQLITE_BUSY under write load.
        int maxPool = settings.isSqlite() ? 1 : settings.maximumPoolSize();
        hikari.setMaximumPoolSize(maxPool);
        hikari.setMinimumIdle(Math.min(settings.minimumIdle(), maxPool));
        hikari.setConnectionTimeout(settings.connectionTimeoutMs());
        hikari.setIdleTimeout(settings.idleTimeoutMs());
        hikari.setMaxLifetime(settings.maxLifetimeMs());
        return new HikariDataSource(hikari);
    }

    @Provides
    @Singleton
    DataSource provideGenericDataSource(HikariDataSource dataSource) {
        return dataSource;
    }

    @Provides
    @Singleton
    Jdbi provideJdbi(DataSource dataSource) {
        return Jdbi.create(dataSource);
    }
}
