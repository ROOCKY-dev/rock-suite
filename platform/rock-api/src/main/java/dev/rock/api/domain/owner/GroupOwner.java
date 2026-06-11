package dev.rock.api.domain.owner;

import java.util.Objects;
import java.util.UUID;

/** Ownership by a group (rank, town, faction). */
public record GroupOwner(UUID id) implements OwnerReference {

    public GroupOwner {
        Objects.requireNonNull(id, "id");
    }

    @Override
    public OwnerType type() {
        return OwnerType.GROUP;
    }
}
