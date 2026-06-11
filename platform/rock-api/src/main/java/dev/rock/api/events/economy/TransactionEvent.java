package dev.rock.api.events.economy;

import dev.rock.api.domain.RockTransaction;
import dev.rock.api.event.AbstractCancellable;
import java.util.Objects;

/** Pre-commit transaction event; cancellable (TRS §8). */
public final class TransactionEvent extends AbstractCancellable {

    private final RockTransaction transaction;

    public TransactionEvent(RockTransaction transaction) {
        this.transaction = Objects.requireNonNull(transaction, "transaction");
    }

    public RockTransaction transaction() {
        return transaction;
    }
}
