package dev.rock.api.domain;

import dev.rock.api.domain.bounds.ClaimBounds;
import dev.rock.api.domain.owner.OwnerReference;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The flagship domain object: an owned region (DMS).
 *
 * @param deletedAt null if active (soft delete — DMS Rule 4)
 */
public record RockClaim(
        UUID id,
        String displayName,
        OwnerReference owner,
        ClaimType type,
        ClaimBounds bounds,
        Instant created,
        Instant modified,
        Instant deletedAt) {

    public RockClaim {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(created, "created");
        Objects.requireNonNull(modified, "modified");
    }

    public boolean active() {
        return deletedAt == null;
    }
}
