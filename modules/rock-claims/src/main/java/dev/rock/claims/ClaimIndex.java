package dev.rock.claims;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.domain.ClaimRole;
import dev.rock.api.domain.RockClaim;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory index of active claims + members for tick-thread-safe protection
 * lookups (TRS §3: no I/O on the tick thread). Maintained by
 * DefaultClaimService on every mutation; warmed from storage on enable.
 */
@RockInternal
final class ClaimIndex {

    private final Map<UUID, List<RockClaim>> byWorld = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, ClaimRole>> membersByClaim = new ConcurrentHashMap<>();

    void load(List<RockClaim> claims, Map<UUID, Map<UUID, ClaimRole>> members) {
        byWorld.clear();
        membersByClaim.clear();
        claims.forEach(this::put);
        members.forEach((claimId, m) -> membersByClaim.put(claimId, new ConcurrentHashMap<>(m)));
    }

    void put(RockClaim claim) {
        List<RockClaim> claims = byWorld.computeIfAbsent(
                claim.bounds().worldId(), k -> new CopyOnWriteArrayList<>());
        claims.removeIf(existing -> existing.id().equals(claim.id()));
        if (claim.active()) {
            claims.add(claim);
        }
    }

    void remove(RockClaim claim) {
        List<RockClaim> claims = byWorld.get(claim.bounds().worldId());
        if (claims != null) {
            claims.removeIf(existing -> existing.id().equals(claim.id()));
        }
        membersByClaim.remove(claim.id());
    }

    Optional<RockClaim> claimAt(UUID worldId, int x, int y, int z) {
        List<RockClaim> claims = byWorld.get(worldId);
        if (claims == null) {
            return Optional.empty();
        }
        for (RockClaim claim : claims) {
            if (claim.bounds().contains(x, y, z)) {
                return Optional.of(claim);
            }
        }
        return Optional.empty();
    }

    void putMember(UUID claimId, UUID playerId, ClaimRole role) {
        membersByClaim.computeIfAbsent(claimId, k -> new ConcurrentHashMap<>()).put(playerId, role);
    }

    void removeMember(UUID claimId, UUID playerId) {
        Map<UUID, ClaimRole> members = membersByClaim.get(claimId);
        if (members != null) {
            members.remove(playerId);
        }
    }

    Optional<ClaimRole> memberRole(UUID claimId, UUID playerId) {
        Map<UUID, ClaimRole> members = membersByClaim.get(claimId);
        return members == null ? Optional.empty() : Optional.ofNullable(members.get(playerId));
    }

    Map<UUID, ClaimRole> membersOf(UUID claimId) {
        Map<UUID, ClaimRole> members = membersByClaim.get(claimId);
        return members == null ? Map.of() : Map.copyOf(members);
    }
}
