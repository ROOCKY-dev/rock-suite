package dev.rock.api.domain;

import dev.rock.api.domain.owner.OwnerReference;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** An account capable of holding value: wallet, town treasury, server bank (DMS). */
public record RockEconomyAccount(
        UUID id,
        OwnerReference owner,
        AccountType type,
        BigDecimal balance,
        Instant deletedAt) {

    public RockEconomyAccount {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(balance, "balance");
    }

    public boolean active() {
        return deletedAt == null;
    }

    public RockEconomyAccount withBalance(BigDecimal newBalance) {
        return new RockEconomyAccount(id, owner, type, newBalance, deletedAt);
    }
}
