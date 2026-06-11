package dev.rock.api.events.economy;

import dev.rock.api.domain.RockEconomyAccount;
import dev.rock.api.event.Event;
import java.math.BigDecimal;
import java.util.Objects;

/** Fired after an account balance changes. */
public record BalanceChangedEvent(RockEconomyAccount account, BigDecimal previousBalance) implements Event {

    public BalanceChangedEvent {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(previousBalance, "previousBalance");
    }
}
