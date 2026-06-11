package dev.rock.api.domain.owner;

import java.util.Objects;
import java.util.UUID;

/** Ownership by another claim (e.g. a town owning sub-claims). */
public record ClaimOwner(UUID id) implements OwnerReference {

    public ClaimOwner {
        Objects.requireNonNull(id, "id");
    }

    @Override
    public OwnerType type() {
        return OwnerType.CLAIM;
    }
}
