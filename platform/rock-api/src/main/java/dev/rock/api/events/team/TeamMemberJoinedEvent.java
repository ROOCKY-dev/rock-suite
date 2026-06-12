package dev.rock.api.events.team;

import dev.rock.api.domain.RockTeam;
import dev.rock.api.domain.TeamRole;
import dev.rock.api.event.Event;
import java.util.Objects;
import java.util.UUID;

/** Fired after a player joins a team (or their role changes). */
public record TeamMemberJoinedEvent(RockTeam team, UUID playerId, TeamRole role) implements Event {

    public TeamMemberJoinedEvent {
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(role, "role");
    }
}
