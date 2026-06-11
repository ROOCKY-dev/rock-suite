package dev.rock.api.events.permission;

import dev.rock.api.domain.RockGroup;
import dev.rock.api.event.Event;
import java.util.Objects;
import java.util.UUID;

/** Fired after a player is added to a group. */
public record RankAssignedEvent(UUID playerId, RockGroup group) implements Event {

    public RankAssignedEvent {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(group, "group");
    }
}
