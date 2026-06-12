package dev.rock.api.command;

import dev.rock.api.domain.RockLocation;
import java.util.UUID;

/** Abstraction over whoever executed a command: player, console, or system. */
public interface CommandSender {

    /** Player UUID, or {@code null} when the sender is the console/system. */
    UUID playerId();

    String name();

    /** The sender's position, or {@code null} when unavailable (console). Since 1.3. */
    default RockLocation location() {
        return null;
    }

    void sendMessage(String message);

    boolean hasPermission(String node);

    default boolean isConsole() {
        return playerId() == null;
    }
}
