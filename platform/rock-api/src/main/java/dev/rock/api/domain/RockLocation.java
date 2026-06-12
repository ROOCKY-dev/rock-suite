package dev.rock.api.domain;

import java.util.Objects;
import java.util.UUID;

/** A position in a world (RPS §16 loader abstraction). */
public record RockLocation(UUID worldId, double x, double y, double z, float yaw, float pitch) {

    public RockLocation {
        Objects.requireNonNull(worldId, "worldId");
    }

    public static RockLocation of(UUID worldId, double x, double y, double z) {
        return new RockLocation(worldId, x, y, z, 0f, 0f);
    }
}
