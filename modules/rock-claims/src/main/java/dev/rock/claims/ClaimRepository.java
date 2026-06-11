package dev.rock.claims;

import dev.rock.api.domain.RockClaim;
import dev.rock.api.domain.owner.OwnerReference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Storage abstraction for claims; backed by DataService in production. */
public interface ClaimRepository {

    CompletableFuture<Void> save(RockClaim claim);

    CompletableFuture<Optional<RockClaim>> findById(UUID id);

    CompletableFuture<List<RockClaim>> findByOwner(OwnerReference owner);

    /** Active claims in a world (used for overlap and containment checks). */
    CompletableFuture<List<RockClaim>> findActiveByWorld(UUID worldId);
}
