package dev.rock.api.events.player;

import dev.rock.api.domain.RockPlayer;
import dev.rock.api.event.Event;
import java.util.Objects;

/** Fired when a player disconnects. */
public record PlayerLeaveEvent(RockPlayer player) implements Event {

    public PlayerLeaveEvent {
        Objects.requireNonNull(player, "player");
    }
}
