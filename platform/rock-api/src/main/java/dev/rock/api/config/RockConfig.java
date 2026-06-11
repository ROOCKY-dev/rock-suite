package dev.rock.api.config;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Read access to a (TOML) configuration tree. Paths use dotted notation,
 * e.g. {@code "database.pool.maximum-pool-size"}. Secrets referenced as
 * {@code "${env.NAME}"} are resolved from the environment (TRS §11).
 */
public interface RockConfig {

    Optional<String> getString(String path);

    Optional<Long> getLong(String path);

    Optional<Integer> getInt(String path);

    Optional<Double> getDouble(String path);

    Optional<Boolean> getBoolean(String path);

    List<String> getStringList(String path);

    boolean contains(String path);

    /** Immediate child keys of the given table path ("" for root). */
    Set<String> keys(String path);

    default String getString(String path, String fallback) {
        return getString(path).orElse(fallback);
    }

    default long getLong(String path, long fallback) {
        return getLong(path).orElse(fallback);
    }

    default int getInt(String path, int fallback) {
        return getInt(path).orElse(fallback);
    }

    default double getDouble(String path, double fallback) {
        return getDouble(path).orElse(fallback);
    }

    default boolean getBoolean(String path, boolean fallback) {
        return getBoolean(path).orElse(fallback);
    }
}
