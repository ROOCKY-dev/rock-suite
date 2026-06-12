package dev.rock.claims;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.domain.ClaimFlag;
import dev.rock.api.domain.ClaimRole;
import dev.rock.api.domain.ClaimType;
import dev.rock.api.domain.RockClaim;
import dev.rock.api.domain.TeamRole;
import dev.rock.api.domain.bounds.ClaimBounds;
import dev.rock.api.domain.owner.GroupOwner;
import dev.rock.api.domain.owner.OwnerReference;
import dev.rock.api.domain.owner.PlayerOwner;
import dev.rock.api.event.EventBus;
import dev.rock.api.events.claim.ClaimCreateEvent;
import dev.rock.api.events.claim.ClaimCreatedEvent;
import dev.rock.api.events.claim.ClaimDeletedEvent;
import dev.rock.api.events.claim.ClaimTransferredEvent;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.api.services.ClaimService;
import dev.rock.api.services.TeamService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
public final class DefaultClaimService implements ClaimService, LifecycleAware {

    private final ClaimRepository repository;
    private final EventBus eventBus;
    private final ServiceRegistry services;
    private final ClaimIndex index = new ClaimIndex();

    @Inject
    public DefaultClaimService(ClaimRepository repository, EventBus eventBus, ServiceRegistry services) {
        this.repository = repository;
        this.eventBus = eventBus;
        this.services = services;
    }

    @Override
    public void onEnable() {
        // Warm the tick-thread-safe index (TRS §3: protection lookups never hit the DB).
        List<RockClaim> claims = repository.findAllActive().join();
        Map<UUID, Map<UUID, ClaimRole>> members = repository.allMembers().join();
        Map<UUID, Map<ClaimFlag, Boolean>> flags = repository.allFlags().join();
        index.load(claims, members, flags);
    }

    @Override
    public void onDisable() {
        index.load(List.of(), Map.of(), Map.of());
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
                index.put(claim);
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
        return CompletableFuture.completedFuture(index.claimAt(worldId, x, y, z));
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
                index.put(transferred);
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
            return repository.save(deleted).thenRun(() -> {
                index.remove(deleted);
                eventBus.publish(new ClaimDeletedEvent(deleted));
            });
        });
    }

    // --- Trust roles (1.1) -------------------------------------------------

    @Override
    public CompletableFuture<Void> trust(UUID claimId, UUID playerId, ClaimRole role) {
        return repository.findById(claimId).thenCompose(found -> {
            found.filter(RockClaim::active).orElseThrow(
                    () -> new ClaimRejectedException("No active claim with id " + claimId));
            return repository.saveMember(claimId, playerId, role)
                    .thenRun(() -> index.putMember(claimId, playerId, role));
        });
    }

    @Override
    public CompletableFuture<Void> untrust(UUID claimId, UUID playerId) {
        return repository.deleteMember(claimId, playerId)
                .thenRun(() -> index.removeMember(claimId, playerId));
    }

    @Override
    public CompletableFuture<Map<UUID, ClaimRole>> membersOf(UUID claimId) {
        return repository.membersOf(claimId);
    }

    // --- Tick-thread-safe cached lookups (1.1) ------------------------------

    @Override
    public Optional<RockClaim> claimAtCached(UUID worldId, int x, int y, int z) {
        return index.claimAt(worldId, x, y, z);
    }

    @Override
    public Optional<ClaimRole> effectiveRole(RockClaim claim, UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        if (claim.owner() instanceof PlayerOwner owner && owner.id().equals(playerId)) {
            return Optional.of(ClaimRole.MANAGER);
        }
        // Team-owned claims (1.3): team membership maps onto claim trust —
        // leaders/officers manage, members build. Explicit per-claim trust
        // (below) can still grant outsiders access.
        if (claim.owner() instanceof GroupOwner group) {
            Optional<ClaimRole> teamRole = services.find(TeamService.class)
                    .flatMap(teams -> teams.roleOfCached(group.id(), playerId))
                    .map(role -> role.atLeast(TeamRole.OFFICER) ? ClaimRole.MANAGER : ClaimRole.BUILD);
            if (teamRole.isPresent()) {
                return teamRole;
            }
        }
        return index.memberRole(claim.id(), playerId);
    }

    // --- Flags (1.2) ---------------------------------------------------------

    @Override
    public CompletableFuture<Void> setFlag(UUID claimId, ClaimFlag flag, boolean value) {
        return repository.findById(claimId).thenCompose(found -> {
            found.filter(RockClaim::active).orElseThrow(
                    () -> new ClaimRejectedException("No active claim with id " + claimId));
            return repository.saveFlag(claimId, flag, value)
                    .thenRun(() -> index.putFlag(claimId, flag, value));
        });
    }

    @Override
    public boolean flag(RockClaim claim, ClaimFlag flag) {
        return index.flag(claim.id(), flag);
    }
}
