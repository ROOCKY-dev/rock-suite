package dev.rock.api.events.claim;

import dev.rock.api.domain.RockClaim;
import dev.rock.api.event.AbstractCancellable;
import java.util.Objects;

/** Pre-action claim creation event; cancellable (TRS §8). */
public final class ClaimCreateEvent extends AbstractCancellable {

    private final RockClaim claim;

    public ClaimCreateEvent(RockClaim claim) {
        this.claim = Objects.requireNonNull(claim, "claim");
    }

    public RockClaim claim() {
        return claim;
    }
}
