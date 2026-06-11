package dev.rock.core.config;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.config.ConfigEngine;
import dev.rock.api.config.RockConfig;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

/** TOML configuration engine (RPS §8). */
@RockInternal
@Singleton
public final class TomlConfigEngine implements ConfigEngine {

    private final Path configDirectory;
    private final Function<String, String> env;

    @Inject
    public TomlConfigEngine(@Named("rock.config.dir") Path configDirectory) {
        this(configDirectory, System::getenv);
    }

    /** Test constructor with controllable environment lookup. */
    public TomlConfigEngine(Path configDirectory, Function<String, String> env) {
        this.configDirectory = configDirectory;
        this.env = env;
    }

    @Override
    public RockConfig parse(String toml) {
        return toConfig(Toml.parse(toml), "<inline>");
    }

    @Override
    public RockConfig load(Path file) {
        try {
            return toConfig(Toml.parse(file), file.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read config file " + file, e);
        }
    }

    @Override
    public RockConfig loadModuleConfig(String moduleId, String defaultToml) {
        Path file = configDirectory.resolve(moduleId + ".toml");
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(configDirectory);
                Files.writeString(file, defaultToml);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create default config " + file, e);
        }
        return load(file);
    }

    private RockConfig toConfig(TomlParseResult result, String source) {
        if (result.hasErrors()) {
            throw new IllegalArgumentException(
                    "Invalid TOML in " + source + ": " + result.errors().get(0).toString());
        }
        return new TomlRockConfig(result, env);
    }
}
