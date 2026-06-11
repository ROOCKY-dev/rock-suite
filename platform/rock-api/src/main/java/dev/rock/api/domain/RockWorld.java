package dev.rock.api.domain;

import java.util.Objects;
import java.util.UUID;

/** A world or dimension (DMS). */
public record RockWorld(UUID id, String name, WorldType type) {

    public RockWorld {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }
}
