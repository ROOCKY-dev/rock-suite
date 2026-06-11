package dev.rock.api.command;

/** Outcome of a command execution. */
public enum CommandResult {
    SUCCESS,
    USAGE_ERROR,
    NO_PERMISSION,
    FAILURE
}
