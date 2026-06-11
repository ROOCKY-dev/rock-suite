package dev.rock.api.command;

import java.util.List;
import java.util.Objects;

/**
 * Declarative command registration. All ROCK commands live under the unified
 * {@code /rock} root (RPS §20): {@code path = ["version"]} → {@code /rock version}.
 *
 * @param path        subcommand path under /rock
 * @param description shown in help output
 * @param permission  required permission node, or empty string for none
 * @param executor    the handler
 */
public record CommandSpec(
        List<String> path,
        String description,
        String permission,
        CommandExecutor executor) {

    public CommandSpec {
        path = List.copyOf(path);
        if (path.isEmpty()) {
            throw new IllegalArgumentException("command path must not be empty");
        }
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(executor, "executor");
    }
}
