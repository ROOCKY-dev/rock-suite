package dev.rock.core.config;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.config.RockConfig;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * TOML-backed config view. Secrets referenced as {@code "${env.NAME}"} are
 * resolved from environment variables at read time (TRS §11).
 */
@RockInternal
final class TomlRockConfig implements RockConfig {

    private final TomlParseResult toml;
    private final Function<String, String> env;

    TomlRockConfig(TomlParseResult toml, Function<String, String> env) {
        this.toml = toml;
        this.env = env;
    }

    private String resolveSecrets(String value) {
        if (value != null && value.startsWith("${env.") && value.endsWith("}")) {
            String name = value.substring("${env.".length(), value.length() - 1);
            String resolved = env.apply(name);
            if (resolved == null) {
                throw new IllegalStateException("Config references unset environment variable: " + name);
            }
            return resolved;
        }
        return value;
    }

    @Override
    public Optional<String> getString(String path) {
        return Optional.ofNullable(toml.getString(path)).map(this::resolveSecrets);
    }

    @Override
    public Optional<Long> getLong(String path) {
        return Optional.ofNullable(toml.getLong(path));
    }

    @Override
    public Optional<Integer> getInt(String path) {
        return getLong(path).map(Math::toIntExact);
    }

    @Override
    public Optional<Double> getDouble(String path) {
        if (toml.isDouble(path)) {
            return Optional.ofNullable(toml.getDouble(path));
        }
        return getLong(path).map(Long::doubleValue);
    }

    @Override
    public Optional<Boolean> getBoolean(String path) {
        return Optional.ofNullable(toml.getBoolean(path));
    }

    @Override
    public List<String> getStringList(String path) {
        TomlArray array = toml.getArray(path);
        if (array == null) {
            return List.of();
        }
        return array.toList().stream().map(String::valueOf).map(this::resolveSecrets).toList();
    }

    @Override
    public boolean contains(String path) {
        return toml.contains(path);
    }

    @Override
    public Set<String> keys(String path) {
        if (path.isEmpty()) {
            return toml.keySet();
        }
        TomlTable table = toml.getTable(path);
        return table == null ? Set.of() : table.keySet();
    }
}
