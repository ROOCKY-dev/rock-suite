package dev.rock.claims;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.domain.ClaimType;
import dev.rock.api.domain.RockClaim;
import dev.rock.api.domain.bounds.ClaimBounds;
import dev.rock.api.domain.owner.OwnerReference;
import dev.rock.api.event.EventBus;
import dev.rock.api.events.claim.ClaimCreateEvent;
import dev.rock.api.events.claim.ClaimCreatedEvent;
import dev.rock.api.events.claim.ClaimDeletedEvent;
import dev.rock.api.events.claim.ClaimTransferredEvent;
import dev.rock.api.services.ClaimService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Claims engine. Creation flow (TRS §8 cancellation contract):
 * ClaimCreateEvent (cancellable, pre) → overlap validation → persist →
 * ClaimCreatedEvent (post). All persistence is async via the repository.
 */
@RockInternal
@Singleton
public final class DefaultClaimService implements ClaimService {

    private final ClaimRepository repository;
    private final EventBus eventBus;

    @Inject
    public DefaultClaimService(ClaimRepository repository, EventBus eventBus) {
        this.repository = repository;
        this.eventBus = eventBus;
    }

    /** Raised when a claim operation is vetoed or invalid. */
    public static final class ClaimRejectedException extends RuntimeException {
        public ClaimRejectedException(String message) {
            super(message);
        }
    }

    @Override
    public CompletableFuture<RockClaim> create(
            String displayName, OwnerReference owner, ClaimType type, ClaimBounds bounds) {
        Instant now = Instant.now();
        RockClaim claim = new RockClaim(UUID.randomUUID(), displayName, owner, type, bounds, now, now, null);

        ClaimCreateEvent preEvent = eventBus.publish(new ClaimCreateEvent(claim));
        if (preEvent.cancelled()) {
            return CompletableFuture.failedFuture(
                    new ClaimRejectedException("Claim creation cancelled by event listener"));
        }

        return repository.findActiveByWorld(bounds.worldId()).thenCompose(existing -> {
            Optional<RockClaim> overlap = existing.stream()
                    .filter(other -> other.bounds().overlaps(bounds))
                    .findFirst();
            if (overlap.isPresent()) {
                return CompletableFuture.failedFuture(new ClaimRejectedException(
                        "Bounds overlap existing claim '" + overlap.get().displayName() + "'"));
            }
            return repository.save(claim).thenApply(v -> {
                eventBus.publish(new ClaimCreatedEvent(claim));
                return claim;
            });
        });
    }

    @Override
    public CompletableFuture<Optional<RockClaim>> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public CompletableFuture<List<RockClaim>> findByOwner(OwnerReference owner) {
        return repository.findByOwner(owner);
    }

    @Override
    public CompletableFuture<Optional<RockClaim>> claimAt(UUID worldId, int x, int y, int z) {
        return repository.findActiveByWorld(worldId).thenApply(claims -> claims.stream()
                .filter(claim -> claim.bounds().contains(x, y, z))
                .findFirst());
    }

    @Override
    public CompletableFuture<RockClaim> transfer(UUID claimId, OwnerReference newOwner) {
        return repository.findById(claimId).thenCompose(found -> {
            RockClaim claim = found.filter(RockClaim::active).orElseThrow(
                    () -> new ClaimRejectedException("No active claim with id " + claimId));
            OwnerReference previousOwner = claim.owner();
            RockClaim transferred = new RockClaim(
                    claim.id(), claim.displayName(), newOwner, claim.type(), claim.bounds(),
                    claim.created(), Instant.now(), null);
            return repository.save(transferred).thenApply(v -> {
                eventBus.publish(new ClaimTransferredEvent(transferred, previousOwner));
                return transferred;
            });
        });
    }

    @Override
    public CompletableFuture<Void> delete(UUID claimId) {
        return repository.findById(claimId).thenCompose(found -> {
            RockClaim claim = found.filter(RockClaim::active).orElseThrow(
                    () -> new ClaimRejectedException("No active claim with id " + claimId));
            // Soft delete (DMS Rule 4): the row stays for audit/grievance queries.
            RockClaim deleted = new RockClaim(
                    claim.id(), claim.displayName(), claim.owner(), claim.type(), claim.bounds(),
                    claim.created(), Instant.now(), Instant.now());
            return repository.save(deleted).thenRun(() -> eventBus.publish(new ClaimDeletedEvent(deleted)));
        });
    }
}
