package dev.rock.api.events.claim;

import dev.rock.api.domain.RockClaim;
import dev.rock.api.event.Event;
import java.util.Objects;

/** Fired after a claim has been persisted. */
public record ClaimCreatedEvent(RockClaim claim) implements Event {

    public ClaimCreatedEvent {
        Objects.requireNonNull(claim, "claim");
    }
}
