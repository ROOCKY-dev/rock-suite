package dev.rock.api.services;

import dev.rock.api.domain.AccountType;
import dev.rock.api.domain.RockEconomyAccount;
import dev.rock.api.domain.RockTransaction;
import dev.rock.api.domain.owner.OwnerReference;
import dev.rock.api.service.RockService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Economy accounts and transactions contract. */
public interface EconomyService extends RockService {

    CompletableFuture<RockEconomyAccount> openAccount(OwnerReference owner, AccountType type);

    CompletableFuture<Optional<RockEconomyAccount>> findAccount(OwnerReference owner);

    CompletableFuture<BigDecimal> balance(UUID accountId);

    /**
     * Atomic transfer between accounts. Returns the committed transaction —
     * status FAILED (with TransactionFailedEvent) when funds are insufficient.
     */
    CompletableFuture<RockTransaction> transfer(
            OwnerReference source, OwnerReference target, BigDecimal amount, String reason);

    /** Reverses a COMPLETED transaction; the reversal links via reversalOf (DMS). */
    CompletableFuture<RockTransaction> reverse(UUID transactionId, String reason);

    CompletableFuture<List<RockTransaction>> history(UUID accountId, int limit);
}
