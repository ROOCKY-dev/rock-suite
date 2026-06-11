package dev.rock.api.events.economy;

import dev.rock.api.domain.RockTransaction;
import dev.rock.api.event.Event;
import java.util.Objects;

/** Fired when a completed transaction is reversed (refund). */
public record TransactionReversedEvent(RockTransaction original, RockTransaction reversal) implements Event {

    public TransactionReversedEvent {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(reversal, "reversal");
    }
}
