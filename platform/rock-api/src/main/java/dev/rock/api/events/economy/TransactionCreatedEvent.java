package dev.rock.api.events.economy;

import dev.rock.api.domain.RockTransaction;
import dev.rock.api.event.Event;
import java.util.Objects;

/** Fired after a transaction completed successfully. */
public record TransactionCreatedEvent(RockTransaction transaction) implements Event {

    public TransactionCreatedEvent {
        Objects.requireNonNull(transaction, "transaction");
    }
}
