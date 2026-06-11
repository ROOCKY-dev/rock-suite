package dev.rock.api.command;

/** Handles execution of a registered command. */
@FunctionalInterface
public interface CommandExecutor {

    CommandResult execute(CommandContext context);
}
