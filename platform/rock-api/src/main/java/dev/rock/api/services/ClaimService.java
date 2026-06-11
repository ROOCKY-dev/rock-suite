package dev.rock.api.services;

import dev.rock.api.domain.ClaimType;
import dev.rock.api.domain.RockClaim;
import dev.rock.api.domain.bounds.ClaimBounds;
import dev.rock.api.domain.owner.OwnerReference;
import dev.rock.api.service.RockService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Claims contract — resolved by other modules via the ServiceRegistry, never imported as an implementation. */
public interface ClaimService extends RockService {

    CompletableFuture<RockClaim> create(String displayName, OwnerReference owner, ClaimType type, ClaimBounds bounds);

    CompletableFuture<Optional<RockClaim>> findById(UUID id);

    CompletableFuture<List<RockClaim>> findByOwner(OwnerReference owner);

    /** The active claim covering a block position, if any. */
    CompletableFuture<Optional<RockClaim>> claimAt(UUID worldId, int x, int y, int z);

    CompletableFuture<RockClaim> transfer(UUID claimId, OwnerReference newOwner);

    /** Soft delete (DMS Rule 4). */
    CompletableFuture<Void> delete(UUID claimId);
}
