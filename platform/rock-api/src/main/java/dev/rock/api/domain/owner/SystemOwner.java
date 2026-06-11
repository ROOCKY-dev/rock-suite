package dev.rock.api.domain.owner;

import java.util.Objects;
import java.util.UUID;

/** Ownership by the platform itself (server treasury, admin claims). */
public record SystemOwner(UUID id) implements OwnerReference {

    /** Well-known id for the singleton server-system owner. */
    public static final UUID SERVER_SYSTEM_ID = new UUID(0L, 0L);

    public SystemOwner {
        Objects.requireNonNull(id, "id");
    }

    public static SystemOwner server() {
        return new SystemOwner(SERVER_SYSTEM_ID);
    }

    @Override
    public OwnerType type() {
        return OwnerType.SYSTEM;
    }
}
