package dev.rock.api.config;

import dev.rock.api.service.RockService;
import java.nio.file.Path;

/**
 * Configuration engine (RPS §8). TOML is the primary format (Charter).
 */
public interface ConfigEngine extends RockService {

    /** Parses a TOML document from a string. */
    RockConfig parse(String toml);

    /** Loads a TOML file from disk. */
    RockConfig load(Path file);

    /**
     * Loads a module's config file from the platform config directory,
     * creating it from the provided default content when absent.
     */
    RockConfig loadModuleConfig(String moduleId, String defaultToml);
}
