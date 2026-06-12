package dev.rock.economy;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.data.DataService;
import dev.rock.api.data.RowMapper;
import dev.rock.api.data.TransactionContext;
import dev.rock.api.domain.AccountType;
import dev.rock.api.domain.RockEconomyAccount;
import dev.rock.api.domain.RockTransaction;
import dev.rock.api.domain.TransactionStatus;
import dev.rock.api.domain.owner.OwnerReference;
import dev.rock.api.domain.owner.SystemOwner;
import dev.rock.api.event.EventBus;
import dev.rock.api.events.economy.BalanceChangedEvent;
import dev.rock.api.events.economy.TransactionCreatedEvent;
import dev.rock.api.events.economy.TransactionEvent;
import dev.rock.api.events.economy.TransactionFailedEvent;
import dev.rock.api.events.economy.TransactionReversedEvent;
import dev.rock.api.services.EconomyService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Economy engine. Transfers are atomic: balance check, both balance updates,
 * and the transaction row commit or roll back together (TRS §17 — zero silent
 * data corruption). Pre-commit listeners can veto via TransactionEvent.
 */
@RockInternal
@Singleton
public final class DefaultEconomyService implements EconomyService {

    private static final RowMapper<RockEconomyAccount> ACCOUNT_MAPPER = row -> new RockEconomyAccount(
            row.getUuid("id"),
            OwnerReference.deserialize(row.getString("owner_ref")),
            AccountType.valueOf(row.getString("account_type")),
            row.getBigDecimal("balance"),
            row.getInstant("deleted_at"));

    private static final RowMapper<RockTransaction> TX_MAPPER = row -> new RockTransaction(
            row.getUuid("id"),
            OwnerReference.deserialize(row.getString("source_account")),
            OwnerReference.deserialize(row.getString("target_account")),
            row.getBigDecimal("amount"),
            TransactionStatus.valueOf(row.getString("status")),
            row.getUuid("reversal_of"),
            row.getInstant("ts"),
            row.getString("reason"));

    /** Raised when an economy operation cannot proceed. */
    public static final class EconomyException extends RuntimeException {
        public EconomyException(String message) {
            super(message);
        }
    }

    private final DataService data;
    private final EventBus eventBus;

    @Inject
    public DefaultEconomyService(DataService data, EventBus eventBus) {
        this.data = data;
        this.eventBus = eventBus;
    }

    @Override
    public CompletableFuture<RockEconomyAccount> openAccount(OwnerReference owner, AccountType type) {
        return data.inTransaction(tx -> {
            Optional<RockEconomyAccount> existing = findByOwner(tx, owner);
            if (existing.isPresent()) {
                return existing.get();
            }
            RockEconomyAccount account =
                    new RockEconomyAccount(UUID.randomUUID(), owner, type, BigDecimal.ZERO, null);
            tx.update("""
                    INSERT INTO rock_accounts (id, owner_ref, account_type, balance, deleted_at)
                    VALUES (:id, :owner, :type, :balance, NULL)
                    """,
                    Map.of("id", account.id().toString(), "owner", owner.serialize(),
                            "type", type.name(), "balance", BigDecimal.ZERO));
            return account;
        });
    }

    @Override
    public CompletableFuture<Optional<RockEconomyAccount>> findAccount(OwnerReference owner) {
        return data.queryOne("SELECT * FROM rock_accounts WHERE owner_ref = :owner AND deleted_at IS NULL",
                Map.of("owner", owner.serialize()), ACCOUNT_MAPPER);
    }

    @Override
    public CompletableFuture<BigDecimal> balance(UUID accountId) {
        return data.queryOne("SELECT balance FROM rock_accounts WHERE id = :id",
                Map.of("id", accountId.toString()), row -> row.getBigDecimal("balance"))
                .thenApply(found -> found.orElseThrow(
                        () -> new EconomyException("No account with id " + accountId)));
    }

    @Override
    public CompletableFuture<RockTransaction> transfer(
            OwnerReference source, OwnerReference target, BigDecimal amount, String reason) {
        RockTransaction proposed = new RockTransaction(UUID.randomUUID(), source, target, amount,
                TransactionStatus.PENDING, null, Instant.now(), reason);

        TransactionEvent preEvent = eventBus.publish(new TransactionEvent(proposed));
        if (preEvent.cancelled()) {
            RockTransaction failed = proposed.withStatus(TransactionStatus.FAILED);
            return persist(failed).thenApply(tx -> {
                eventBus.publish(new TransactionFailedEvent(failed, "Cancelled by event listener"));
                return tx;
            });
        }

        return data.inTransaction(tx -> {
            RockEconomyAccount from = findByOwner(tx, source)
                    .orElseThrow(() -> new EconomyException("No source account for " + source.serialize()));
            RockEconomyAccount to = findByOwner(tx, target)
                    .orElseThrow(() -> new EconomyException("No target account for " + target.serialize()));

            if (from.balance().compareTo(amount) < 0) {
                insert(tx, proposed.withStatus(TransactionStatus.FAILED));
                return new TransferOutcome(proposed.withStatus(TransactionStatus.FAILED),
                        "Insufficient funds", null, null);
            }

            updateBalance(tx, from.id(), from.balance().subtract(amount));
            updateBalance(tx, to.id(), to.balance().add(amount));
            RockTransaction completed = proposed.withStatus(TransactionStatus.COMPLETED);
            insert(tx, completed);
            return new TransferOutcome(completed, null,
                    from.withBalance(from.balance().subtract(amount)),
                    to.withBalance(to.balance().add(amount)));
        }).thenApply(outcome -> {
            if (outcome.failureReason() != null) {
                eventBus.publish(new TransactionFailedEvent(outcome.transaction(), outcome.failureReason()));
            } else {
                eventBus.publish(new TransactionCreatedEvent(outcome.transaction()));
                eventBus.publish(new BalanceChangedEvent(outcome.source(),
                        outcome.source().balance().add(outcome.transaction().amount())));
                eventBus.publish(new BalanceChangedEvent(outcome.target(),
                        outcome.target().balance().subtract(outcome.transaction().amount())));
            }
            return outcome.transaction();
        });
    }

    private record TransferOutcome(
            RockTransaction transaction, String failureReason,
            RockEconomyAccount source, RockEconomyAccount target) {
    }

    @Override
    public CompletableFuture<RockTransaction> reverse(UUID transactionId, String reason) {
        return data.inTransaction(tx -> {
            RockTransaction original = tx.queryOne("SELECT * FROM rock_transactions WHERE id = :id",
                            Map.of("id", transactionId.toString()), TX_MAPPER)
                    .orElseThrow(() -> new EconomyException("No transaction " + transactionId));
            if (original.status() != TransactionStatus.COMPLETED) {
                throw new EconomyException("Only COMPLETED transactions can be reversed, was " + original.status());
            }

            RockEconomyAccount source = findByOwner(tx, original.sourceAccount())
                    .orElseThrow(() -> new EconomyException("Source account gone"));
            RockEconomyAccount target = findByOwner(tx, original.targetAccount())
                    .orElseThrow(() -> new EconomyException("Target account gone"));

            // Money flows back; the reversal links to the original (DMS).
            updateBalance(tx, target.id(), target.balance().subtract(original.amount()));
            updateBalance(tx, source.id(), source.balance().add(original.amount()));
            tx.update("UPDATE rock_transactions SET status = :status WHERE id = :id",
                    Map.of("status", TransactionStatus.REVERSED.name(), "id", original.id().toString()));

            RockTransaction reversal = new RockTransaction(UUID.randomUUID(),
                    original.targetAccount(), original.sourceAccount(), original.amount(),
                    TransactionStatus.COMPLETED, original.id(), Instant.now(), reason);
            insert(tx, reversal);
            return new RockTransaction[] {original.withStatus(TransactionStatus.REVERSED), reversal};
        }).thenApply(pair -> {
            eventBus.publish(new TransactionReversedEvent(pair[0], pair[1]));
            return pair[1];
        });
    }

    @Override
    public CompletableFuture<List<RockTransaction>> history(UUID accountId, int limit) {
        return data.queryOne("SELECT owner_ref FROM rock_accounts WHERE id = :id",
                        Map.of("id", accountId.toString()), row -> row.getString("owner_ref"))
                .thenCompose(ownerRef -> {
                    String owner = ownerRef.orElseThrow(
                            () -> new EconomyException("No account with id " + accountId));
                    return data.query("""
                            SELECT * FROM rock_transactions
                            WHERE source_account = :owner OR target_account = :owner
                            ORDER BY ts DESC LIMIT :limit
                            """,
                            Map.of("owner", owner, "limit", limit), TX_MAPPER);
                });
    }

    @Override
    public CompletableFuture<RockTransaction> grant(OwnerReference target, BigDecimal amount, String reason) {
        if (amount.signum() <= 0) {
            return CompletableFuture.failedFuture(new EconomyException("Grant amount must be positive"));
        }
        return data.inTransaction(tx -> {
            RockEconomyAccount account = findByOwner(tx, target)
                    .orElseThrow(() -> new EconomyException("No account for " + target.serialize()
                            + " — open it first"));
            // System mint: credits without debiting the SYSTEM owner. The
            // transaction row keeps money creation visible and auditable.
            updateBalance(tx, account.id(), account.balance().add(amount));
            RockTransaction mint = new RockTransaction(UUID.randomUUID(),
                    SystemOwner.server(), target, amount,
                    TransactionStatus.COMPLETED, null, Instant.now(), reason);
            insert(tx, mint);
            return new Object[] {mint, account.withBalance(account.balance().add(amount))};
        }).thenApply(pair -> {
            RockTransaction mint = (RockTransaction) pair[0];
            RockEconomyAccount account = (RockEconomyAccount) pair[1];
            eventBus.publish(new TransactionCreatedEvent(mint));
            eventBus.publish(new BalanceChangedEvent(account, account.balance().subtract(amount)));
            return mint;
        });
    }

    @Override
    public CompletableFuture<List<RockEconomyAccount>> topBalances(int limit) {
        return data.query("""
                SELECT * FROM rock_accounts
                WHERE deleted_at IS NULL AND account_type = 'PLAYER'
                ORDER BY balance DESC LIMIT :limit
                """, Map.of("limit", limit), ACCOUNT_MAPPER);
    }

    private static Optional<RockEconomyAccount> findByOwner(TransactionContext tx, OwnerReference owner) {
        return tx.queryOne("SELECT * FROM rock_accounts WHERE owner_ref = :owner AND deleted_at IS NULL",
                Map.of("owner", owner.serialize()), ACCOUNT_MAPPER);
    }

    private static void updateBalance(TransactionContext tx, UUID accountId, BigDecimal newBalance) {
        tx.update("UPDATE rock_accounts SET balance = :balance WHERE id = :id",
                Map.of("balance", newBalance, "id", accountId.toString()));
    }

    private static void insert(TransactionContext tx, RockTransaction transaction) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", transaction.id().toString());
        params.put("source", transaction.sourceAccount().serialize());
        params.put("target", transaction.targetAccount().serialize());
        params.put("amount", transaction.amount());
        params.put("status", transaction.status().name());
        params.put("reversal_of", transaction.reversalOf() == null ? null : transaction.reversalOf().toString());
        params.put("ts", transaction.timestamp().toEpochMilli());
        params.put("reason", transaction.reason());
        tx.update("""
                INSERT INTO rock_transactions (id, source_account, target_account, amount, status, reversal_of, ts, reason)
                VALUES (:id, :source, :target, :amount, :status, :reversal_of, :ts, :reason)
                """, params);
    }

    private CompletableFuture<RockTransaction> persist(RockTransaction transaction) {
        return data.inTransaction(tx -> {
            insert(tx, transaction);
            return transaction;
        });
    }
}
