package dev.rock.api.domain.owner;

import java.util.Objects;
import java.util.UUID;

/** Ownership by an individual player. */
public record PlayerOwner(UUID id) implements OwnerReference {

    public PlayerOwner {
        Objects.requireNonNull(id, "id");
    }

    @Override
    public OwnerType type() {
        return OwnerType.PLAYER;
    }
}
