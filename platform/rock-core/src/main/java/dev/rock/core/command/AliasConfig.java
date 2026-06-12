package dev.rock.core.command;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.command.CommandService;
import dev.rock.api.config.RockConfig;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads short command aliases (Project Owner request): a config-driven table
 * mapping a root command to a /rock subtree prefix, so {@code /ban} runs
 * {@code /rock ban} and {@code /r} is a {@code /rock} shorthand.
 *
 * <p>Sane built-in defaults; each alias is individually disable-able so servers
 * can dodge collisions with other mods' commands. Config shape (rock.toml):
 *
 * <pre>{@code
 * [aliases]
 * enabled = true                 # master switch
 * ban = ["ban"]                  # /ban → /rock ban
 * r = []                         # /r   → /rock
 * home = false                   # disable a default alias
 * mywarp = ["warp"]              # add a custom one
 * }</pre>
 *
 * Loader adapters call {@link #apply} after boot, then register each resolved
 * alias name as a real root command that delegates to
 * {@link CommandService#dispatchAlias}.
 */
@RockInternal
public final class AliasConfig {

    private static final Logger log = LoggerFactory.getLogger(AliasConfig.class);

    /** Built-in aliases: name → /rock subtree prefix. */
    public static final Map<String, List<String>> DEFAULTS = Map.ofEntries(
            Map.entry("r", List.of()),
            Map.entry("ban", List.of("ban")),
            Map.entry("unban", List.of("unban")),
            Map.entry("mute", List.of("mute")),
            Map.entry("unmute", List.of("unmute")),
            Map.entry("warn", List.of("warn")),
            Map.entry("kick", List.of("kick")),
            Map.entry("home", List.of("home")),
            Map.entry("sethome", List.of("sethome")),
            Map.entry("homes", List.of("homes")),
            Map.entry("warp", List.of("warp")),
            Map.entry("tpa", List.of("tpa")),
            Map.entry("tpaccept", List.of("tpaccept")),
            Map.entry("tpdeny", List.of("tpdeny")),
            Map.entry("pay", List.of("pay")),
            Map.entry("balance", List.of("balance")),
            Map.entry("bal", List.of("balance")),
            Map.entry("baltop", List.of("baltop")));

    /** Reserved keys under [aliases] that are settings, not aliases. */
    private static final Set<String> RESERVED = Set.of("enabled");

    private AliasConfig() {
    }

    /**
     * Resolves the effective alias table from config layered over defaults,
     * registers each into the CommandService, and returns the names registered
     * (so the adapter can wire matching brigadier roots).
     */
    public static Set<String> apply(RockConfig config, CommandService commands) {
        if (!config.getBoolean("aliases.enabled", true)) {
            log.info("Command aliases disabled by config");
            return Set.of();
        }

        Map<String, List<String>> effective = new java.util.TreeMap<>(DEFAULTS);

        // Per-alias overrides: false disables a default; a string list (re)maps it.
        for (String name : config.keys("aliases")) {
            if (RESERVED.contains(name)) {
                continue;
            }
            String path = "aliases." + name;
            if (config.getBoolean(path).filter(b -> !b).isPresent()) {
                effective.remove(name);
                continue;
            }
            List<String> expansion = config.getStringList(path);
            // A bare "name = true" keeps the default mapping if there is one,
            // otherwise maps the alias to its own name as the subcommand.
            if (expansion.isEmpty() && config.getBoolean(path).orElse(false)) {
                effective.putIfAbsent(name, List.of(name));
            } else if (!expansion.isEmpty()) {
                effective.put(name, expansion);
            }
        }

        effective.forEach((name, expansion) -> {
            if (commands.registerAlias(name, expansion)) {
                log.debug("Alias /{} → /rock {}", name, String.join(" ", expansion));
            }
        });
        log.info("Registered {} command alias(es)", effective.size());
        return effective.keySet();
    }
}
