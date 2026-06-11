package dev.rock.data;

import dev.rock.api.config.RockConfig;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Connection-pool configuration (TRS §5 recommended defaults).
 *
 * @param username nullable for SQLite
 * @param password nullable for SQLite; secrets resolve via ${env.*} (TRS §11)
 */
public record DatabaseSettings(
        String jdbcUrl,
        String username,
        String password,
        int maximumPoolSize,
        int minimumIdle,
        long connectionTimeoutMs,
        long idleTimeoutMs,
        long maxLifetimeMs) {

    public DatabaseSettings {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
    }

    public boolean isSqlite() {
        return jdbcUrl.startsWith("jdbc:sqlite:");
    }

    /** Zero-config default: SQLite file in the platform data directory (TRS §5 Tier 1). */
    public static DatabaseSettings sqliteDefault(Path dataDirectory) {
        return new DatabaseSettings(
                "jdbc:sqlite:" + dataDirectory.resolve("rock.db"), null, null, 10, 2, 30_000, 600_000, 1_800_000);
    }

    public static DatabaseSettings fromConfig(RockConfig config, Path dataDirectory) {
        DatabaseSettings defaults = sqliteDefault(dataDirectory);
        return new DatabaseSettings(
                config.getString("database.url", defaults.jdbcUrl()),
                config.getString("database.username", null),
                config.getString("database.password", null),
                config.getInt("database.pool.maximum-pool-size", defaults.maximumPoolSize()),
                config.getInt("database.pool.minimum-idle", defaults.minimumIdle()),
                config.getLong("database.pool.connection-timeout", defaults.connectionTimeoutMs()),
                config.getLong("database.pool.idle-timeout", defaults.idleTimeoutMs()),
                config.getLong("database.pool.max-lifetime", defaults.maxLifetimeMs()));
    }
}
