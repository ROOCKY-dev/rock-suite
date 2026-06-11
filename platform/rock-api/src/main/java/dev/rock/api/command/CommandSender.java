package dev.rock.api.command;

import java.util.UUID;

/** Abstraction over whoever executed a command: player, console, or system. */
public interface CommandSender {

    /** Player UUID, or {@code null} when the sender is the console/system. */
    UUID playerId();

    String name();

    void sendMessage(String message);

    boolean hasPermission(String node);

    default boolean isConsole() {
        return playerId() == null;
    }
}
