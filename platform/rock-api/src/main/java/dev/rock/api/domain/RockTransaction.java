package dev.rock.api.domain;

import dev.rock.api.domain.owner.OwnerReference;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A value transfer between accounts (DMS).
 *
 * @param reversalOf nullable — links to the original transaction if this is a reversal
 */
public record RockTransaction(
        UUID id,
        OwnerReference sourceAccount,
        OwnerReference targetAccount,
        BigDecimal amount,
        TransactionStatus status,
        UUID reversalOf,
        Instant timestamp,
        String reason) {

    public RockTransaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceAccount, "sourceAccount");
        Objects.requireNonNull(targetAccount, "targetAccount");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(reason, "reason");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("transaction amount must be positive");
        }
    }

    public RockTransaction withStatus(TransactionStatus newStatus) {
        return new RockTransaction(id, sourceAccount, targetAccount, amount, newStatus, reversalOf, timestamp, reason);
    }
}
