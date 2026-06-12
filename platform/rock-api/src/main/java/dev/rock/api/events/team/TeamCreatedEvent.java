package dev.rock.api.events.team;

import dev.rock.api.domain.RockTeam;
import dev.rock.api.event.Event;
import java.util.Objects;
import java.util.UUID;

/** Fired after a team is created. */
public record TeamCreatedEvent(RockTeam team, UUID leader) implements Event {

    public TeamCreatedEvent {
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(leader, "leader");
    }
}
