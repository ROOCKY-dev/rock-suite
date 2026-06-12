package dev.rock.api.events.player;

import dev.rock.api.event.AbstractCancellable;
import java.util.Objects;
import java.util.UUID;

/**
 * Pre-broadcast chat event (platform keystone K1 family). Published on the
 * tick/network thread by loader adapters before the message is distributed;
 * cancelling suppresses it (mutes, filters). Consumers like rock-discord
 * bridge it outward at LAST priority.
 */
public final class PlayerChatEvent extends AbstractCancellable {

    private final UUID actor;
    private final String username;
    private final String message;

    public PlayerChatEvent(UUID actor, String username, String message) {
        this.actor = Objects.requireNonNull(actor, "actor");
        this.username = Objects.requireNonNull(username, "username");
        this.message = Objects.requireNonNull(message, "message");
    }

    public UUID actor() {
        return actor;
    }

    public String username() {
        return username;
    }

    public String message() {
        return message;
    }
}
