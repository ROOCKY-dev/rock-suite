package dev.rock.core.command;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.command.CommandContext;
import dev.rock.api.command.CommandResult;
import dev.rock.api.command.CommandSender;
import dev.rock.api.command.CommandService;
import dev.rock.api.command.CommandSpec;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified /rock command tree (RPS §20). Dispatch matches the longest
 * registered subcommand path, checks the permission node, then executes.
 */
@RockInternal
@Singleton
public final class DefaultCommandService implements CommandService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCommandService.class);

    private final Map<String, CommandSpec> commands = new ConcurrentHashMap<>();
    private final Map<String, List<String>> aliases = new ConcurrentHashMap<>();

    private static String key(List<String> path) {
        return String.join(" ", path).toLowerCase();
    }

    @Override
    public void register(CommandSpec spec) {
        CommandSpec existing = commands.putIfAbsent(key(spec.path()), spec);
        if (existing != null) {
            throw new IllegalStateException("Command already registered: /rock " + key(spec.path()));
        }
    }

    @Override
    public boolean unregister(List<String> path) {
        return commands.remove(key(path)) != null;
    }

    @Override
    public CommandResult dispatch(CommandSender sender, List<String> rawArgs) {
        // Longest-prefix match so "/rock claims create ..." resolves the
        // ["claims", "create"] spec even with trailing arguments.
        for (int len = rawArgs.size(); len > 0; len--) {
            CommandSpec spec = commands.get(key(rawArgs.subList(0, len)));
            if (spec == null) {
                continue;
            }
            if (!spec.permission().isEmpty() && !sender.hasPermission(spec.permission())) {
                sender.sendMessage("You do not have permission to run this command.");
                return CommandResult.NO_PERMISSION;
            }
            List<String> args = rawArgs.subList(len, rawArgs.size());
            try {
                return spec.executor().execute(new CommandContext(sender, key(spec.path()), args));
            } catch (Exception e) {
                log.error("Command /rock {} threw", key(spec.path()), e);
                sender.sendMessage("An internal error occurred while executing the command.");
                return CommandResult.FAILURE;
            }
        }
        sender.sendMessage("Unknown command. Available: " + commands.keySet().stream().sorted().toList());
        return CommandResult.USAGE_ERROR;
    }

    @Override
    public List<CommandSpec> registered() {
        List<CommandSpec> specs = new ArrayList<>(commands.values());
        specs.sort(Comparator.comparing(s -> key(s.path())));
        return List.copyOf(specs);
    }

    // --- Aliases (1.6) ------------------------------------------------------

    @Override
    public boolean registerAlias(String alias, List<String> expansion) {
        return aliases.putIfAbsent(alias.toLowerCase(), List.copyOf(expansion)) == null;
    }

    @Override
    public boolean unregisterAlias(String alias) {
        return aliases.remove(alias.toLowerCase()) != null;
    }

    @Override
    public Map<String, List<String>> aliases() {
        return Map.copyOf(aliases);
    }

    @Override
    public CommandResult dispatchAlias(CommandSender sender, String alias, List<String> args) {
        List<String> expansion = aliases.get(alias.toLowerCase());
        if (expansion == null) {
            return dispatch(sender, args);
        }
        List<String> full = new ArrayList<>(expansion);
        full.addAll(args);
        return dispatch(sender, full);
    }
}
