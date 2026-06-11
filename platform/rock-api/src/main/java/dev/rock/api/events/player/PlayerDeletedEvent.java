package dev.rock.api.events.player;

import dev.rock.api.event.Event;
import java.util.Objects;
import java.util.UUID;

/**
 * GDPR erasure event (TRS §22): modules must delete or anonymise any personal
 * data they hold for this player when they receive it.
 */
public record PlayerDeletedEvent(UUID playerId) implements Event {

    public PlayerDeletedEvent {
        Objects.requireNonNull(playerId, "playerId");
    }
}
