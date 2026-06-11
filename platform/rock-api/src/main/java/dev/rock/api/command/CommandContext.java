package dev.rock.api.command;

import java.util.List;
import java.util.Objects;

/**
 * Per-execution command state (command scope, DIS §6).
 *
 * @param sender who executed the command
 * @param label  the alias the command was invoked with
 * @param args   arguments after the (sub)command path
 */
public record CommandContext(CommandSender sender, String label, List<String> args) {

    public CommandContext {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(label, "label");
        args = List.copyOf(args);
    }

    public String arg(int index, String fallback) {
        return index < args.size() ? args.get(index) : fallback;
    }
}
