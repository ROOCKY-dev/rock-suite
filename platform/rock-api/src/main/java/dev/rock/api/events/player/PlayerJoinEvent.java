package dev.rock.api.events.player;

import dev.rock.api.domain.RockPlayer;
import dev.rock.api.event.Event;
import java.util.Objects;

/** Fired when a player joins the server (Alpha success criterion, REH §18). */
public record PlayerJoinEvent(RockPlayer player, boolean firstJoin) implements Event {

    public PlayerJoinEvent {
        Objects.requireNonNull(player, "player");
    }
}
