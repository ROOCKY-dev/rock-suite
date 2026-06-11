package dev.rock.api.domain;

import java.util.Objects;
import java.util.UUID;

/** The running Minecraft server (DMS). */
public record RockServer(UUID id, String name, String version, ServerType type) {

    public RockServer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(type, "type");
    }
}
