package dev.rock.api.events.team;

import dev.rock.api.domain.RockTeam;
import dev.rock.api.event.Event;
import java.util.Objects;
import java.util.UUID;

/** Fired after a player leaves (or is removed from) a team. */
public record TeamMemberLeftEvent(RockTeam team, UUID playerId) implements Event {

    public TeamMemberLeftEvent {
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(playerId, "playerId");
    }
}
