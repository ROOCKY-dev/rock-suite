package dev.rock.api.events.claim;

import dev.rock.api.domain.RockClaim;
import dev.rock.api.event.Event;
import java.util.Objects;

/** Fired after a claim is soft-deleted. */
public record ClaimDeletedEvent(RockClaim claim) implements Event {

    public ClaimDeletedEvent {
        Objects.requireNonNull(claim, "claim");
    }
}
