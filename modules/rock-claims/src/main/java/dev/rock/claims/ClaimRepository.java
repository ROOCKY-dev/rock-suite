package dev.rock.claims;

import dev.rock.api.domain.ClaimRole;
import dev.rock.api.domain.RockClaim;
import dev.rock.api.domain.owner.OwnerReference;
import java.util.List;
import java.util.Map;
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

    /** All active claims (index warm-up on enable). */
    CompletableFuture<List<RockClaim>> findAllActive();

    CompletableFuture<Void> saveMember(UUID claimId, UUID playerId, ClaimRole role);

    CompletableFuture<Void> deleteMember(UUID claimId, UUID playerId);

    CompletableFuture<Map<UUID, ClaimRole>> membersOf(UUID claimId);

    /** claimId → (playerId → role) for all claims (index warm-up). */
    CompletableFuture<Map<UUID, Map<UUID, ClaimRole>>> allMembers();
}
