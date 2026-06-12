package dev.rock.core.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rock.api.config.RockConfig;
import dev.rock.core.config.TomlConfigEngine;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AliasConfigTest {

    @TempDir
    Path tempDir;

    private RockConfig parse(String toml) {
        return new TomlConfigEngine(tempDir, Map.<String, String>of()::get).parse(toml);
    }

    @Test
    void defaultsApplyWhenNoOverrides() {
        DefaultCommandService commands = new DefaultCommandService();

        AliasConfig.apply(parse("[aliases]\nenabled = true\n"), commands);

        Map<String, List<String>> aliases = commands.aliases();
        assertEquals(List.of("ban"), aliases.get("ban"));
        assertEquals(List.of(), aliases.get("r"), "/r is a bare /rock shorthand");
        assertEquals(List.of("balance"), aliases.get("bal"), "bal → balance");
    }

    @Test
    void masterSwitchDisablesEverything() {
        DefaultCommandService commands = new DefaultCommandService();

        AliasConfig.apply(parse("[aliases]\nenabled = false\n"), commands);

        assertTrue(commands.aliases().isEmpty());
    }

    @Test
    void perAliasDisableAndCustomMapping() {
        DefaultCommandService commands = new DefaultCommandService();

        AliasConfig.apply(parse("""
                [aliases]
                enabled = true
                home = false
                mywarp = ["warp"]
                """), commands);

        Map<String, List<String>> aliases = commands.aliases();
        assertFalse(aliases.containsKey("home"), "disabled default is dropped");
        assertEquals(List.of("warp"), aliases.get("mywarp"), "custom alias added");
        assertTrue(aliases.containsKey("ban"), "other defaults survive");
    }

    @Test
    void remapAnExistingAlias() {
        DefaultCommandService commands = new DefaultCommandService();

        AliasConfig.apply(parse("""
                [aliases]
                enabled = true
                r = ["claims"]
                """), commands);

        assertEquals(List.of("claims"), commands.aliases().get("r"), "/r remapped to /rock claims");
    }
}
