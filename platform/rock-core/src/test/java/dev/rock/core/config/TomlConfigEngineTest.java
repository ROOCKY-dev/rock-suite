package dev.rock.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rock.api.config.RockConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TomlConfigEngineTest {

    @TempDir
    Path tempDir;

    private TomlConfigEngine engine(Map<String, String> env) {
        return new TomlConfigEngine(tempDir, env::get);
    }

    @Test
    void parsesDottedPathsAndTypes() {
        RockConfig config = engine(Map.of()).parse("""
                [database.pool]
                maximum-pool-size = 10
                minimum-idle = 2

                [claims]
                tax-rate = 5.5
                enabled = true
                worlds = ["overworld", "nether"]
                """);

        assertEquals(10, config.getInt("database.pool.maximum-pool-size", -1));
        assertEquals(2, config.getInt("database.pool.minimum-idle", -1));
        assertEquals(5.5, config.getDouble("claims.tax-rate", -1));
        assertTrue(config.getBoolean("claims.enabled", false));
        assertEquals(java.util.List.of("overworld", "nether"), config.getStringList("claims.worlds"));
        assertEquals("fallback", config.getString("missing.key", "fallback"));
        assertTrue(config.keys("database").contains("pool"));
    }

    @Test
    void resolvesEnvSecrets() {
        RockConfig config = engine(Map.of("ROCK_DB_PASSWORD", "s3cret"))
                .parse("password = \"${env.ROCK_DB_PASSWORD}\"");

        assertEquals("s3cret", config.getString("password", null));
    }

    @Test
    void unsetSecretFailsLoudly() {
        RockConfig config = engine(Map.of()).parse("password = \"${env.NOT_SET}\"");

        assertThrows(IllegalStateException.class, () -> config.getString("password"));
    }

    @Test
    void invalidTomlIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> engine(Map.of()).parse("= broken ="));
    }

    @Test
    void loadModuleConfigCreatesDefaultFileOnce() throws Exception {
        TomlConfigEngine engine = engine(Map.of());

        RockConfig first = engine.loadModuleConfig("rock-claims", "max-claims = 5\n");
        assertEquals(5, first.getInt("max-claims", -1));
        assertTrue(Files.exists(tempDir.resolve("rock-claims.toml")));

        // Existing file is respected, not overwritten.
        Files.writeString(tempDir.resolve("rock-claims.toml"), "max-claims = 99\n");
        assertEquals(99, engine.loadModuleConfig("rock-claims", "max-claims = 5\n").getInt("max-claims", -1));
    }
}
