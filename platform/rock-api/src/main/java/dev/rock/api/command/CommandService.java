package dev.rock.api.command;

import dev.rock.api.service.RockService;
import java.util.List;

/** Centralized command framework (RPS §20). */
public interface CommandService extends RockService {

    /** Registers a subcommand under the unified /rock root. */
    void register(CommandSpec spec);

    boolean unregister(List<String> path);

    /** Dispatches a raw /rock invocation; used by loader adapters. */
    CommandResult dispatch(CommandSender sender, List<String> rawArgs);

    List<CommandSpec> registered();
}
