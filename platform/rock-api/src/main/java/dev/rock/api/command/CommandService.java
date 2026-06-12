package dev.rock.api.command;

import dev.rock.api.service.RockService;
import java.util.List;
import java.util.Map;

/** Centralized command framework (RPS §20). */
public interface CommandService extends RockService {

    /** Registers a subcommand under the unified /rock root. */
    void register(CommandSpec spec);

    boolean unregister(List<String> path);

    /** Dispatches a raw /rock invocation; used by loader adapters. */
    CommandResult dispatch(CommandSender sender, List<String> rawArgs);

    List<CommandSpec> registered();

    // --- Aliases (since 1.6) ------------------------------------------------

    /**
     * Registers a short root command that expands into the /rock tree.
     * {@code "ban" → ["ban"]} makes {@code /ban x} behave as {@code /rock ban x};
     * {@code "r" → []} makes {@code /r} a bare {@code /rock} shorthand. Pure
     * routing — the expanded command's permission node is still enforced.
     *
     * @return false if the alias name was already registered
     */
    boolean registerAlias(String alias, List<String> expansion);

    boolean unregisterAlias(String alias);

    /** All registered aliases (alias name → expansion prefix). */
    Map<String, List<String>> aliases();

    /**
     * Dispatches a short-alias invocation: prepends the alias expansion to the
     * given args, then dispatches as a normal /rock command. Used by loader
     * adapters when a registered alias root is invoked.
     */
    CommandResult dispatchAlias(CommandSender sender, String alias, List<String> args);
}
