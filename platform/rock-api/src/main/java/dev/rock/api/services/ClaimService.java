package dev.rock.api.services;

import dev.rock.api.domain.ClaimFlag;
import dev.rock.api.domain.ClaimRole;
import dev.rock.api.domain.ClaimType;
import dev.rock.api.domain.RockClaim;
import dev.rock.api.domain.bounds.ClaimBounds;
import dev.rock.api.domain.owner.OwnerReference;
import dev.rock.api.service.RockService;
import java.util.List;
import java.util.Map;
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

    // --- Trust roles (since 1.1) ------------------------------------------

    /** Grants or updates a member's trust role on a claim. */
    CompletableFuture<Void> trust(UUID claimId, UUID playerId, ClaimRole role);

    CompletableFuture<Void> untrust(UUID claimId, UUID playerId);

    CompletableFuture<Map<UUID, ClaimRole>> membersOf(UUID claimId);

    // --- Tick-thread-safe cached lookups (since 1.1) ----------------------

    /**
     * Cache-backed claim lookup, safe on the tick thread — used by protection
     * listeners. The cache is maintained by the claims module and may lag
     * persistence by an in-flight write.
     */
    Optional<RockClaim> claimAtCached(UUID worldId, int x, int y, int z);

    /**
     * Effective trust role of a player on a claim (cache-backed, tick-safe).
     * The claim owner (PLAYER-owned claims) is implicitly MANAGER.
     */
    Optional<ClaimRole> effectiveRole(RockClaim claim, UUID playerId);

    // --- Flags (since 1.2) --------------------------------------------------

    /** Sets a per-claim rule toggle; persisted via platform metadata. */
    CompletableFuture<Void> setFlag(UUID claimId, ClaimFlag flag, boolean value);

    /** Cache-backed flag read, tick-thread safe; falls back to the flag default. */
    boolean flag(RockClaim claim, ClaimFlag flag);
}
