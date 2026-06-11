package dev.rock.api.domain.owner;

import java.util.UUID;

/**
 * Abstracts ownership so claims, economy accounts, and other ownable entities
 * can be owned by players, groups, claims, or the system without the data
 * model assuming player ownership (DMS).
 *
 * <p>Stored in the database as a single VARCHAR column in the compact
 * {@code "TYPE:uuid"} format — no join needed to identify owner type.
 */
public sealed interface OwnerReference permits PlayerOwner, GroupOwner, ClaimOwner, SystemOwner {

    UUID id();

    OwnerType type();

    /** Compact DB-safe form, e.g. {@code "PLAYER:a1b2c3d4-..."}. */
    default String serialize() {
        return type().name() + ":" + id();
    }

    static OwnerReference of(OwnerType type, UUID id) {
        return switch (type) {
            case PLAYER -> new PlayerOwner(id);
            case GROUP -> new GroupOwner(id);
            case CLAIM -> new ClaimOwner(id);
            case SYSTEM -> new SystemOwner(id);
        };
    }

    /** Parses the {@code "TYPE:uuid"} format produced by {@link #serialize()}. */
    static OwnerReference deserialize(String value) {
        int sep = value.indexOf(':');
        if (sep < 1) {
            throw new IllegalArgumentException("Invalid OwnerReference: " + value);
        }
        OwnerType type = OwnerType.valueOf(value.substring(0, sep));
        UUID id = UUID.fromString(value.substring(sep + 1));
        return of(type, id);
    }
}
