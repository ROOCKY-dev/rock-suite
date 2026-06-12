package dev.rock.api.events.team;

import dev.rock.api.domain.RockTeam;
import dev.rock.api.event.Event;
import java.util.Objects;

/** Fired after a team is disbanded (soft-deleted). */
public record TeamDisbandedEvent(RockTeam team) implements Event {

    public TeamDisbandedEvent {
        Objects.requireNonNull(team, "team");
    }
}
