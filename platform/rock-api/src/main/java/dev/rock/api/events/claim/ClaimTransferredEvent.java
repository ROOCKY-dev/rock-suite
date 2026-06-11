package dev.rock.api.events.claim;

import dev.rock.api.domain.RockClaim;
import dev.rock.api.domain.owner.OwnerReference;
import dev.rock.api.event.Event;
import java.util.Objects;

/** Fired after claim ownership changes. */
public record ClaimTransferredEvent(RockClaim claim, OwnerReference previousOwner) implements Event {

    public ClaimTransferredEvent {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(previousOwner, "previousOwner");
    }
}
