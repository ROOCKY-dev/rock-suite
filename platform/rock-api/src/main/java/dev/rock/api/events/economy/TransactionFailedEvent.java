package dev.rock.api.events.economy;

import dev.rock.api.domain.RockTransaction;
import dev.rock.api.event.Event;
import java.util.Objects;

/** Fired when a transaction could not complete (e.g. insufficient funds). */
public record TransactionFailedEvent(RockTransaction transaction, String failureReason) implements Event {

    public TransactionFailedEvent {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(failureReason, "failureReason");
    }
}
